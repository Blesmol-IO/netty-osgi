package io.blesmol.netty.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.DynamicChannelHandler;
import io.blesmol.netty.api.DynamicHandlerEvents;
import io.blesmol.netty.api.EventExecutorGroupHandler;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.api.ReferenceName;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.util.concurrent.EventExecutorGroup;

@Component(configurationPid = Configuration.DYNAMIC_CHANNEL_HANDLER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = DynamicChannelHandler.class, immediate = true)
public class DynamicChannelHandlerProvider extends ChannelInboundHandlerAdapter implements DynamicChannelHandler {

	// This handler's context
	private final Deferred<ChannelHandlerContext> deferredContext = new Deferred<>();
	private final Promise<ChannelHandlerContext> promisedContext = deferredContext.getPromise();

	// Set in activate
	private String channelId;
	private String pid;
	private String appName;
	private String inetHost;
	private int inetPort;

	// Extra handler properties
	private volatile Optional<Map<String, Object>> extraProperties;

	@SuppressWarnings("unused")
	private volatile Boolean closed = false;
	private static final AtomicReferenceFieldUpdater<DynamicChannelHandlerProvider, Boolean> CLOSED = AtomicReferenceFieldUpdater
			.newUpdater(DynamicChannelHandlerProvider.class, Boolean.class, "closed");

	// The outbound deferred is resolved when the outbound sends us an event, first
	// time only
	// The promise is updated each time via a field updater
	private volatile Deferred<DynamicHandlerEvents> outboundHandlerDeferred = new Deferred<DynamicHandlerEvents>();
	private volatile Promise<?> outboundHandlerPromise = outboundHandlerDeferred.getPromise();
	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<DynamicChannelHandlerProvider, Promise> OUTBOUND_PROMISE = AtomicReferenceFieldUpdater
			.newUpdater(DynamicChannelHandlerProvider.class, Promise.class, "outboundHandlerPromise");
	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<DynamicChannelHandlerProvider, Deferred> OUTBOUND_DEFERRED = AtomicReferenceFieldUpdater
			.newUpdater(DynamicChannelHandlerProvider.class, Deferred.class, "outboundHandlerDeferred");

	// Signifies when all handler configs have been tasked to be added
	private volatile Deferred<Future<?>> maybeAddToPipelineDeferred = new Deferred<>();
	private volatile Promise<Future<?>> maybeAddToPipelinePromise = maybeAddToPipelineDeferred.getPromise();

	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<DynamicChannelHandlerProvider, Deferred> MAYBE_ADD_PIPELINE_DEFERRED = AtomicReferenceFieldUpdater
			.newUpdater(DynamicChannelHandlerProvider.class, Deferred.class, "maybeAddToPipelineDeferred");
	@SuppressWarnings("rawtypes")
	private static final AtomicReferenceFieldUpdater<DynamicChannelHandlerProvider, Promise> MAYBE_ADD_PIPELINE_PROMISE = AtomicReferenceFieldUpdater
			.newUpdater(DynamicChannelHandlerProvider.class, Promise.class, "maybeAddToPipelinePromise");

	// Needs volatile since it can be updated on modified method calls
	// after activate method, possibly on a different thread.
	private volatile Configuration.DynamicChannelHandler priorConfig;

	// Deque of activated or modified promises
	private final ConcurrentLinkedDeque<Promise<List<Void>>> promises = new ConcurrentLinkedDeque<>();

	// Holds the defers for channel handlers; resolved asynchronously when a handler
	// is set
	private final Map<HandlerNameFactoryPid, Deferred<ChannelHandler>> deferredHandlers = new ConcurrentHashMap<>();
	// Holds promises for configurations generated by configuration admin, resolved
	// when created
	private final Map<HandlerNameFactoryPid, Promise<org.osgi.service.cm.Configuration>> configurations = new ConcurrentHashMap<>();
	// Lookup map between keys and handlers
	private final Map<HandlerNameFactoryPid, ChannelHandler> keysToHandlers = new ConcurrentHashMap<>();
	// Holds empty promises for keys added to pipeline
	private final Map<HandlerNameFactoryPid, Promise<Void>> keyedPipeline = new ConcurrentHashMap<>();

	//
	// OSGI FIELD AND METHOD REFERENCES
	//

	// Use the context executor for IO operations
	@Reference
	ExecutorService executor;

	@Reference
	ConfigurationAdmin configAdmin;

	@Reference
	ConfigurationUtil configUtil;

	// Note: target filter is on channel ID
	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE, name = ReferenceName.DynamicChannelHandler.CHANNEL_HANDLER)
	void setChannelHandler(ChannelHandler handler, Map<String, Object> props) {

		// TODO log
		System.out.println(String.format("Setting channel handler %s on %s", props.get(Constants.SERVICE_PID), this));
		String handlerName = null;
		String factoryPid = null;
		try {
			handlerName = (String) props.get(Property.ChannelHandler.HANDLER_NAME);
			factoryPid = (String) props.get(ConfigurationAdmin.SERVICE_FACTORYPID);
		} catch (NullPointerException e) {
			// TODO: log warning
			String errMessage = "Error: ignoring handler '%s'; does not have expected property keys '%s' or '%s' in its map:\n%s";
			System.err.println(String.format(errMessage, handler.toString(), Property.ChannelHandler.HANDLER_NAME,
					ConfigurationAdmin.SERVICE_FACTORYPID, props));
			return;
		}

		final HandlerNameFactoryPid key = new HandlerNameFactoryPid(handlerName, factoryPid);

		if (!deferredHandlers.containsKey(key)) {
			// TODO: log warning
			System.err.println(String.format("Error: key '%s' does not exist in deferred handler map, ignoring", key));
			return;
		}

		// Putting into the map happens-before resolving
		keysToHandlers.put(key, handler);

		// Resolve handler
		deferredHandlers.get(key).resolve(handler);

	}

	void unsetChannelHandler(ChannelHandler handler, Map<String, Object> props) {

		// TODO log
		System.out.println(String.format("Unsetting channel handler %s on %s", handler, this));
		String handlerName = null;
		String factoryPid = null;
		try {
			handlerName = (String) props.get(Property.ChannelHandler.HANDLER_NAME);
			factoryPid = (String) props.get(ConfigurationAdmin.SERVICE_FACTORYPID);
		} catch (NullPointerException e) {
			// TODO: log warning
			String errMessage = "Error: ignoring handler '%s'; does not have expected property keys '%s' or '%s' in its map:\n%s";
			System.err.println(String.format(errMessage, handler.toString(), Property.ChannelHandler.HANDLER_NAME,
					ConfigurationAdmin.SERVICE_FACTORYPID, props));
			return;
		}

		final HandlerNameFactoryPid key = new HandlerNameFactoryPid(handlerName, factoryPid);

		// Remove previously resolved handler
		deferredHandlers.remove(key);

		// If this handler was added, remove
		if (keyedPipeline.remove(key) != null) {
			promisedContext.then((p) -> removeFromPipeline(key, handler, p.getValue()));
			System.out.println(String.format("Unsetted channel handler %s:%s:%s in %s", handler, key.factoryPid,
					key.handlerName, this));

		}

		System.out.println(String.format("Leaving unsetChannelHandler in %s", this));
	}

	// Currently a no-op so as not to unset and then set the reference.
	void updatedChannelHandler(ChannelHandler handler) {
	}

	@Activate
	void activate(Configuration.DynamicChannelHandler config, Map<String, Object> props) {

		updateProperties(config, props);
		System.out.println("Activating " + this);

		// Update extra properties
		extraProperties = configUtil.toOptionalExtraProperties(props);

		this.priorConfig = config;

		final List<HandlerNameFactoryPid> keys = toKeys(config.handlerNames(), config.factoryPids());

		addOrUpdateConfigurationsAndHandlers(keys);

		// TODO: log
		System.out.println("Activated " + this);

	}

	// only call in activate
	void updateProperties(Configuration.DynamicChannelHandler config, Map<String, Object> properties) {
		channelId = config.channelId();
		pid = (String) properties.get(Constants.SERVICE_PID);
		appName = config.appName();
		inetHost = config.inetHost();
		inetPort = config.inetPort();
	}

	@Modified
	void modified(Configuration.DynamicChannelHandler config, Map<String, Object> props) {

		assert config.factoryPids().length == config.handlerNames().length;

		System.out.println("Modifying " + this);

		// Update our properties
		extraProperties = configUtil.toOptionalExtraProperties(props);
		Configuration.DynamicChannelHandler priorConfig = this.priorConfig;
		this.priorConfig = config;

		// Create our sets used for filtering
		final Set<HandlerNameFactoryPid> theseKeys = IntStream.range(0, config.factoryPids().length)
				.mapToObj(i -> new HandlerNameFactoryPid(config.handlerNames()[i], config.factoryPids()[i]))
				.collect(Collectors.toSet());

		// Union of the above sets
		final Set<HandlerNameFactoryPid> allKeys = IntStream.range(0, priorConfig.factoryPids().length)
				.mapToObj(i -> new HandlerNameFactoryPid(priorConfig.handlerNames()[i], priorConfig.factoryPids()[i]))
				.collect(Collectors.toSet());
		allKeys.addAll(theseKeys);

		// Remove the keys that don't exist in this config
		final Set<HandlerNameFactoryPid> toRemove = allKeys.stream().filter(it -> !theseKeys.contains(it))
				.collect(Collectors.toSet());

		// Get promise created via prior activate or modified method call
		// and chain off of it
		promises.remove()
				// Remove the cruft first
				.then((p) -> deleteFactoryConfigurations(toRemove))
				// Then add / update everything else
				.then((p) -> addOrUpdateConfigurationsAndHandlers(toKeys(config.handlerNames(), config.factoryPids())));

		System.out.println("Modified " + this);
	}

	@Deactivate
	void deactivate(Configuration.DynamicChannelHandler config, Map<String, ?> properties) {

		System.out.println("Deactivating dynamic handler properties: " + properties);

		priorConfig = config;
		close();
		System.out.println("Deactivated " + this);
	}

	private void close() {

		// exit quickly if we've already been called
		if (CLOSED.compareAndSet(this, false, true)) {

			Configuration.DynamicChannelHandler config = this.priorConfig;

			// Chain this promise off the existing one in the queue, which there should
			// always be one. What we create we must destroy
			promises.remove()
					.then((p) -> deleteFactoryConfigurations(IntStream.range(0, config.handlerNames().length)
							.mapToObj(i -> new HandlerNameFactoryPid(config.handlerNames()[i], config.factoryPids()[i]))
							.collect(Collectors.toList())));
		}
	}

	private List<HandlerNameFactoryPid> toKeys(String[] handlerNames, String[] factoryPids) {

		assert handlerNames.length == factoryPids.length;

		return IntStream.range(0, handlerNames.length)
				.mapToObj(it -> new HandlerNameFactoryPid(handlerNames[it], factoryPids[it]))
				.collect(Collectors.toList());
	}

	//
	// PROMISE METHODS
	//

	private Promise<Void> addOrUpdateConfigurationsAndHandlers(final List<HandlerNameFactoryPid> keys) {

		// Ensure we're not blocking activate method by running on a different thread
		executor.execute(new Runnable() {

			@Override
			public void run() {
				Promise<List<Void>> offerredPromises =
						// First create the managed service factory configurations
						maybeCreateFactoryConfigurations(keys)
								// Then update the configurations with required properties
								.then((p) -> maybeUpdateFactoryConfigurations(p.getValue(), keys, channelId))
								// And finally add to the pipeline if not already added
								.then((p) -> maybeAddToPipeline(p.getValue(), promisedContext.getValue(), keys),
										(f) -> f.getFailure().printStackTrace());
				System.out.println("Offering promises to dequeue: " + offerredPromises);
				promises.offer(offerredPromises);
			}
		});
		return Promises.resolved(null);
	}

	/*
	 * Create a list of configuration promises
	 */
	private Promise<List<org.osgi.service.cm.Configuration>> maybeCreateFactoryConfigurations(
			List<HandlerNameFactoryPid> keys) {
		final Deferred<List<org.osgi.service.cm.Configuration>> results = new Deferred<>();

		executor.execute(new Runnable() {
			@Override
			public void run() {
				final List<Promise<org.osgi.service.cm.Configuration>> promisedConfigs = new ArrayList<>();
				keys.stream().forEachOrdered(it -> {

					Promise<org.osgi.service.cm.Configuration> promise = configurations.get(it);
					if (promise == null) {
						Deferred<org.osgi.service.cm.Configuration> deferred = new Deferred<>();
						try {
							final org.osgi.service.cm.Configuration configuration = configAdmin
									.createFactoryConfiguration(it.factoryPid, "?");

							// Store configurations for future deletion
							// This happens-before updating the configuration
							DynamicChannelHandlerProvider.this.configurations.put(it, deferred.getPromise());
							deferred.resolve(configuration);
							System.out.println(
									String.format("Created configuration for '%s:%s'", it.factoryPid, it.handlerName));
						} catch (Exception e) {
							e.printStackTrace();
							deferred.fail(e);
						}
						promise = deferred.getPromise();
					}
					promisedConfigs.add(promise);
				});
				results.resolveWith(Promises.all(promisedConfigs));
			}
		});

		System.out.println("Finished maybeCreateFactoryConfigurations in " + this);
		return results.getPromise();
	}

	/*
	 * 
	 */
	private Promise<List<ChannelHandler>> maybeUpdateFactoryConfigurations(
			List<org.osgi.service.cm.Configuration> configs, List<HandlerNameFactoryPid> keys, String channelId) {

		Deferred<List<ChannelHandler>> result = new Deferred<>();

		executor.execute(new Runnable() {

			@Override
			public void run() {

				final List<Promise<ChannelHandler>> promises = new ArrayList<>(configs.size());
				IntStream.range(0, configs.size()).forEachOrdered(i -> {

					final HandlerNameFactoryPid key = keys.get(i);

					Deferred<ChannelHandler> deferred = deferredHandlers.get(key);
					if (deferred == null) {
						deferred = new Deferred<>();
						final org.osgi.service.cm.Configuration c = configs.get(i);
						final String handlerName = key.handlerName;

						// Putting in the concurrent maps needs to happen-before updating the
						// configuration
						deferredHandlers.put(key, deferred);

						// Fail the channel handler defer if its expected configuration cannot be
						// updated
						try {
							c.update(
									configUtil.channelHandlerProperties(appName, inetHost, inetPort, handlerName, channelId, extraProperties));
							System.out.println(
									String.format("Updated configuration for %s:%s:%s:%s", appName, key.factoryPid, handlerName, channelId));
						} catch (Exception e) {
							e.printStackTrace();
							deferred.fail(e);
						}
					}
					promises.add(deferred.getPromise());
				});
				System.out.println("updateConfigsAndMaps resolving promise in " + this);
				result.resolveWith(Promises.all(promises));

			}
		});
		System.out.println("Finished maybeUpdateFactoryConfigurations in " + this);
		return result.getPromise();
	}

	private Promise<List<Void>> maybeAddToPipeline(List<ChannelHandler> handlers, ChannelHandlerContext context,
			List<HandlerNameFactoryPid> keys) {

		Deferred<List<Void>> result = new Deferred<>();

		System.out.println("Starting maybeAddToPipeline in " + this);
		maybeAddToPipelineDeferred.resolve(executor.submit(() -> {
			System.out.println("Submitted maybeAddToPipeline in " + this);
			final List<Promise<Void>> promises = new ArrayList<>();
			final ChannelPipeline pipeline = context.pipeline();
			final EventLoop channelEventLoop = context.channel().eventLoop();
			final String channelId = context.channel().id().asLongText();

			// NEEDS TO BE ORDERED
			// The passed in keys are ordered correctly
			// The list of handlers may not be ordered
			keys.stream().forEachOrdered(it -> {

				Promise<Void> promise = keyedPipeline.get(it);

				// The handler has not been added yet
				if (promise == null) {
					Deferred<Void> deferred = new Deferred<>();
					final ChannelHandler handler = keysToHandlers.get(it);
					final int idx = keys.indexOf(it);
					final String handlerName = it.handlerName;
					try {
						// a null event executor group is the same as using the channel's event loop, so
						// set it here
						final EventExecutorGroup eventExecutorGroup = handler instanceof EventExecutorGroupHandler
								? ((EventExecutorGroupHandler) handler).getEventExecutorGroup()
								: channelEventLoop;

						Runnable toAdd = null;
						// If this is the first handler, add after this dynamic handler
						if (idx == 0) {
							toAdd = () -> {
								pipeline.addAfter(eventExecutorGroup, DynamicChannelHandler.HANDLER_NAME, handlerName,
										handler);
								System.out.println(
										String.format("Added handler '%s' after dynamic handler", handlerName));
								deferred.resolve(null);
							};
						}
						// Otherwise, use the previously added (just now or before) handler as a guide
						else {
							toAdd = () -> {
								String priorHandlerName = keys.get(idx - 1).handlerName;
								pipeline.addAfter(eventExecutorGroup, priorHandlerName, handlerName, handler);
								System.out.println(
										String.format("Added handler '%s' after '%s'", handlerName, priorHandlerName));
								deferred.resolve(null);
							};
						}

						// Since we're using the channel's event loop to execute the runnable, we
						// ensure adding handlers sequentially.
						System.out.println(String.format("Executing runnable to add handler %s on channel id %s via %s",
								handlerName, channelId, this));
						channelEventLoop.execute(toAdd);

						// deferred.resolve(null);
					} catch (Exception e) {
						e.printStackTrace();
						deferred.fail(e);
					}
					promise = deferred.getPromise();
					keyedPipeline.put(it, promise);
				}
				promises.add(promise);
			});

			// Add the outbound dynamic handler, which signals us when it's
			// added, using our executor so as to receive events whilst still being added
			// Ensure it's added to via event loop of the channel, but use our executor
			channelEventLoop.execute(() -> {
				System.out.println("Adding outbound dynamic handler");
				pipeline.addLast(context.executor(), DynamicOutboundChannelHandler.HANDLER_NAME,
						new DynamicOutboundChannelHandler());
			});

			// Now allow reading of channel
			channelEventLoop.execute(() -> {
				System.out.println(
						String.format("Enabling auto read on channel id %s", context.channel().id().asLongText()));
				context.channel().config().setAutoRead(true);
			});
			// and resolve
			result.resolveWith(Promises.all(promises));
		}));

		// Update promise
		MAYBE_ADD_PIPELINE_PROMISE.set(this, maybeAddToPipelineDeferred.getPromise());
		MAYBE_ADD_PIPELINE_DEFERRED.set(this, new Deferred<Future<?>>());

		System.out.println("Finished maybeAddToPipeline in " + this);
		return result.getPromise();
	}

	private Promise<Void> removeFromPipeline(HandlerNameFactoryPid key, ChannelHandler handler,
			ChannelHandlerContext context) {
		final Deferred<Void> deferred = new Deferred<>();
		try {
			// Only attempt to remove the handler if the channel is still active
			if (context.channel().isActive() && context.pipeline().get(key.handlerName) != null) {
				context.pipeline().remove(handler);
				System.out.println(String.format("Removed handler '%s' from channelId", key.handlerName, channelId));
			}
			deferred.resolve(null);
		} catch (Exception e) {
			e.printStackTrace();
			deferred.fail(e);
		}
		System.out.println("Finished removeFromPipeline in " + this);
		return deferred.getPromise();
	}

	/*
	 * Parallel delete configurations
	 */
	private Promise<List<Void>> deleteFactoryConfigurations(Collection<HandlerNameFactoryPid> keys) {
		final Deferred<List<Void>> results = new Deferred<>();

		executor.execute(new Runnable() {
			@Override
			public void run() {
				List<Promise<Void>> deletedConfigs = new CopyOnWriteArrayList<>();
				keys.parallelStream().forEach((k) -> {
					final Deferred<Void> deferred = new Deferred<>();
					try {
						System.out.println(
								String.format("Deleting factory configuration for %s:%s", k.factoryPid, k.handlerName));
						org.osgi.service.cm.Configuration c = configurations.remove(k).getValue();
						c.delete();
						deferred.resolve(null);
					} catch (Exception e) {
						e.printStackTrace();
						deferred.fail(e);
					}
					deletedConfigs.add(deferred.getPromise());
				});
				System.out.println(String
						.format("Resolved deleteFactoryConfigurations for " + DynamicChannelHandlerProvider.this));
				results.resolveWith(Promises.all(deletedConfigs));
			}
		});
		System.out.println("Finished deleteFactoryConfigurations in " + this);
		return results.getPromise();
	}

	//
	// Dynamic handler methods
	//

	@Override
	public Promise<Future<?>> handlersConfigured() {
		return maybeAddToPipelinePromise;
	}

	//
	// NETTY SERVICE METHODS
	//

	@Override
	public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
		System.out.println(this + " added");
		deferredContext.resolve(ctx);
	}

	// TODO: close
	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		System.out.println(this + " removed");
		close();
	}

	// For below methods, wrap the call in a promise that'll resolve when the
	// dynamic channel outbound handler is added to the pipeline

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		System.out.println(String.format("%s read called with promise %s", this, outboundHandlerPromise));
		outboundHandlerPromise.onResolve(new Runnable() {
			@Override
			public void run() {
				System.out.println(DynamicChannelHandlerProvider.this + " read");
				try {
					DynamicChannelHandlerProvider.super.channelRead(ctx, msg);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		System.out.println(String.format("%s read complete called with promise %s", this, outboundHandlerPromise));
		outboundHandlerPromise.onResolve(new Runnable() {
			@Override
			public void run() {
				System.out.println(DynamicChannelHandlerProvider.this + " read complete");
				try {
					DynamicChannelHandlerProvider.super.channelReadComplete(ctx);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		// If this is a promise, see if it's from the outbound communicating to us
		// Deferred<Void> outboundHandlerDeferred = this.outboundHandlerDeferred;
		// System.out.println("Outbound deferred in user event triggered: " +
		// outboundHandlerDeferred);
		if (evt instanceof Promise<?>) {
			try {
				@SuppressWarnings("unchecked")
				Promise<DynamicHandlerEvents> eventPromise = (Promise<DynamicHandlerEvents>) evt;
				System.out.println(String.format("Getting event promise in " + this));
				switch (eventPromise.getValue()) {
				case LAST_HANDLER_ADDED:
					System.out.println(
							String.format("Resolving deferred related to promise: %s", outboundHandlerPromise));
					// First resolve the deferred, which resolves any outstanding promise callbacks
					outboundHandlerDeferred.resolveWith(eventPromise);
					// Update the promise with the newly updated deferred promise
					// No-op first time around
					OUTBOUND_PROMISE.set(this, outboundHandlerDeferred.getPromise());
					// Then update the deferred so it is ready to resolve the next time around
					final Deferred<Void> newOutboundHandlerDeferred = new Deferred<>();
					OUTBOUND_DEFERRED.set(this, newOutboundHandlerDeferred);
					System.out.println(String.format("New promise: %s", outboundHandlerDeferred.getPromise()));
					return;
				default:
					break;

				}
			} catch (ClassCastException e) {

			}

		}
		// Otherwise send the event onwards
		System.out.println(this + " user event triggered called");
		outboundHandlerPromise.onResolve(new Runnable() {
			@Override
			public void run() {
				try {
					System.out.println(this + " user event triggered, calling super");
					DynamicChannelHandlerProvider.super.userEventTriggered(ctx, evt);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		System.out.println(this + " active called");
		outboundHandlerPromise.onResolve(new Runnable() {
			@Override
			public void run() {
				System.out.println(this + " activte");
				try {
					DynamicChannelHandlerProvider.super.channelActive(ctx);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		System.out.println(this + " inactive called");
		outboundHandlerPromise.onResolve(new Runnable() {
			@Override
			public void run() {
				System.out.println(this + " inactive");
				try {
					DynamicChannelHandlerProvider.super.channelInactive(ctx);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
		System.out.println(this + " channel writability changed called");
		outboundHandlerPromise.onResolve(new Runnable() {
			@Override
			public void run() {
				System.out.println(this + " channel writability changed");
				try {
					DynamicChannelHandlerProvider.super.channelWritabilityChanged(ctx);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
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
		close();
	}

	@Override
	public String toString() {
		return String.format("%s:%s:%s:%d:%s", pid, appName, inetHost, inetPort, channelId);
	}

}
