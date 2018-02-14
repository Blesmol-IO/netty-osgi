package io.blesmol.netty.provider;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.OsgiChannelHandler;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.api.ReferenceName;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@Component(configurationPid = Configuration.OSGI_CHANNEL_HANDLER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, scope = ServiceScope.PROTOTYPE)
public class OsgiChannelHandlerProvider extends ChannelInboundHandlerAdapter implements OsgiChannelHandler {

	// Does not need volatile since it's set in activate method
	private Promise<List<ChannelHandler>> activatedHandlers;

	// Needs volatile since it's set in a service method
	private volatile Promise<ChannelHandlerContext> contextPromise;

	// Needs volatile since it can be updated on modified method calls
	// after activate method, possibly on a different thread
	private volatile Configuration.OsgiChannelHandler config;

	// Concurrency
	private final ConcurrentMap<String, ChannelHandler> referencedHandlers = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Deferred<ChannelHandler>> deferrers = new ConcurrentHashMap<>();

	// Guards
	private final AtomicBoolean deferrersReady = new AtomicBoolean(false);

	//
	// OSGI FIELD AND METHOD REFERENCES
	//
//	@Reference
//	ExecutorService executor;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL, name = "channelHandler")
	void setChannelHandler(ChannelHandler handler, Map<String, Object> props) {
		try {
			String handlerName = (String) props.get(Property.ChannelHandler.HANDLE_NAME);
			referencedHandlers.put(handlerName, handler);

			// Only modify existing deferrers after the activate method says so
			// TODO: Consider turning into a promise which adds the services after the
			// promise is fulfilled.
			if (deferrersReady.get()) {

				// Resolve the deferred with this expected handler.
				if (deferrers.containsKey(handlerName)) {
					Deferred<ChannelHandler> d = deferrers.get(handlerName);

					if (!d.getPromise().isDone()) {
						deferrers.get(handlerName).resolve(handler);
					}
				}
			}

		} catch (NullPointerException e) {
			// TODO: log
			String errMessage = "Error: set handler '%s' does not have a string property named '%s', ignoring. Is the '%s' target set correctly? Properties:\n%s";
			System.err.println(String.format(errMessage, handler.toString(), Property.ChannelHandler.HANDLE_NAME,
					ReferenceName.OsgiChannelHandler.CHANNEL_HANDLER, props));
		}
	}

	void unsetChannelHandler(ChannelHandler handler, Map<String, Object> props) {
		try {
			referencedHandlers.remove((String) props.get(Property.ChannelHandler.HANDLE_NAME), handler);
		} catch (NullPointerException e) {
			// TODO: log
			String errMessage = "Error: unset handler '%s' does not have a string property named '%s', ignoring. Is the '%s' target set correctly?";
			System.err.println(String.format(errMessage, handler.toString(), Property.ChannelHandler.HANDLE_NAME,
					ReferenceName.OsgiChannelHandler.CHANNEL_HANDLER));
		}
	}

	// Currently a no-op so as not to unset and then set the reference.
	void updatedChannelHandler(ChannelHandler handler) {
	}

	/*
	 * Create maps of deferrers and promises based on configured handlers
	 */
	@Activate
	void activate(Configuration.OsgiChannelHandler config) {

		this.config = config;

		// Populate maps containing the defers.
		//
		// This is against the contract of a defer, which is suppose to be immutable
		// and not marked as thread safe. (Their promises are thread safe.)
		final LinkedHashMap<String, Deferred<ChannelHandler>> deferrers = new LinkedHashMap<>(config.handlers().length); // elided
		Arrays.stream(config.handlers()).forEach(h -> {
			Deferred<ChannelHandler> d = new Deferred<>();
			deferrers.put(h, d);
			if (referencedHandlers.containsKey(h)) {
				d.resolve(referencedHandlers.get(h));
			}
		});
		this.deferrers.putAll(deferrers);

		// Indicate to the set channel handler method that it can update this map now.
		deferrersReady.set(true);

		// Update our activated promise to be the ordered promises of all
		// previously added deferred handler promises.
		activatedHandlers = Promises
				.all(deferrers.values().stream().map(d -> d.getPromise()).collect(Collectors.toList()));

	}

	/*
	 * OSGi 6.0 Cmpn 112.5.12 Modification:
	 * 
	 * ...
	 * 
	 * 2. Call the modified method. See Modified Method on page 320.
	 * 
	 * 3. Modify the bound services for the dynamic references if the set of target
	 * services changed due to changes in the target properties. See Bound Service
	 * Replacement on page 319.
	 * 
	 * ...
	 * 
	 * Paraphrasing, modified method is called and then any bound services are
	 * removed. So the reference map of handlers may contain invalidly targeted
	 * handlers that ought to be removed. That means we can't assume the current map
	 * contains valid ones. Therefore, filter base on the modified handler
	 * configuration.
	 * 
	 */
	@Modified
	void modified(Configuration.OsgiChannelHandler config, Map<String, ?> properties) {

		String[] previousHandlers = this.config.handlers();
		String[] modifiedHandlers = config.handlers();
		this.config = config;

		activatedHandlers.onResolve(new Runnable() {

			@Override
			public void run() {
				modifyHandlers(previousHandlers, modifiedHandlers);
			}
		});

	}

	/*
	 * Note: being called by OSGi modified method, within a runnable FIXME: run this
	 * on an executor thread TODO: test me!
	 */
	private void modifyHandlers(String[] previousHandlers, String[] modifiedHandlers) {

		// Create our sets used for filtering
		final Set<String> previous = new HashSet<>(Arrays.asList(previousHandlers));
		final Set<String> modified = new HashSet<>(Arrays.asList(modifiedHandlers));
		final Set<String> all = new HashSet<>(previous);
		all.addAll(modified);

		final Set<String> toRemove = new HashSet<>(all);
		toRemove.removeAll(modified);

		final Set<String> toAdd = new HashSet<>(all);
		toAdd.removeAll(previous);

		contextPromise.onResolve(new Runnable() {

			@Override
			public void run() {
				ChannelHandlerContext ctx;
				try {
					ctx = contextPromise.getValue();

					// Remove those handlers from the pipeline which need removal
					toRemove.forEach(h -> {
						ctx.pipeline().remove(h);
					});

					// Using the current ordered configuration, add only those needing to be added,
					// maintaining order.
					IntStream.range(0, modifiedHandlers.length).filter(i -> toAdd.contains(modifiedHandlers[i]))
							.forEach(i -> {
								// If this is the first handler, add as first. No name needed
								if (i == 0) {
									ctx.pipeline().addFirst(referencedHandlers.get(modifiedHandlers[i]));
								}
								// Otherwise, use the previous handler, either just added or previously added,
								// as a reference and add after it.
								else {
									ctx.pipeline().addAfter(modifiedHandlers[i - 1], modifiedHandlers[i],
											referencedHandlers.get(modifiedHandlers[i]));
								}
							});
				} catch (InvocationTargetException | InterruptedException e) {
					// Dunno when this is reachable?
					e.printStackTrace();
				}
			}
		});

	}

	@Deactivate
	void deactivate(Configuration.OsgiChannelHandler config, Map<String, ?> properties) {
		// TODO: do anything?
		// Called when configuration is removed, bundle is going away, channel is closed
		// (via channel init)
	}

	//
	// SERVICE METHODS
	//

	/*
	 * (non-Javadoc)
	 * 
	 * Fulfill the context promise, and after activation add initial handlers to
	 * pipeline
	 * 
	 * @see io.netty.channel.ChannelHandlerAdapter#handlerAdded(io.netty.channel.
	 * ChannelHandlerContext)
	 */
	@Override
	public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {

		// Set our context in a promise for modified method
		Deferred<ChannelHandlerContext> deferredContext = new Deferred<>();
		deferredContext.resolve(ctx);
		contextPromise = deferredContext.getPromise();

		// On initial activation, add all expected handlers on the pipeline,
		// in the order they were configured.
		activatedHandlers.onResolve(() -> {
			try {
				activatedHandlers.getValue().stream()
						//
						.forEach(h -> ctx.pipeline().addLast(h));
			} catch (InvocationTargetException | InterruptedException e1) {
				// TODO log, what else?
				e1.printStackTrace();
			}
		});
	}

	/*
	 * (non-Javadoc)
	 * 
	 * Close the connection on exception.
	 * 
	 * @see io.netty.channel.ChannelInboundHandlerAdapter#exceptionCaught(io.netty.
	 * channel.ChannelHandlerContext, java.lang.Throwable)
	 */
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		ctx.close();
	}
}
