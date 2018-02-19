package io.blesmol.netty.provider;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import org.osgi.service.component.annotations.ServiceScope;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.OsgiChannelHandler;
import io.blesmol.netty.api.Property;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@Component(configurationPid = Configuration.OSGI_CHANNEL_HANDLER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class OsgiChannelHandlerProvider extends ChannelInboundHandlerAdapter implements OsgiChannelHandler {

	private final AtomicBoolean readyToRead = new AtomicBoolean(false);
	
	private final Deferred<ChannelHandlerContext> deferredContext = new Deferred<>();

	// Needs volatile since it can be updated on modified method calls
	// after activate method, possibly on a different thread.
	private volatile Configuration.OsgiChannelHandler config;

	// Deque of activated or modified promises
	private final ConcurrentLinkedDeque<Promise<List<Void>>> promises = new ConcurrentLinkedDeque<>();

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final Map<Key, Deferred<ChannelHandler>> deferrers = new ConcurrentHashMap<>();
	private final Map<Key, org.osgi.service.cm.Configuration> configurations = new ConcurrentHashMap<>();
	private final Map<ChannelHandler, Promise<Void>> addedToPipeline = new ConcurrentHashMap<>();

	//
	// OSGI FIELD AND METHOD REFERENCES
	//

	@Reference
	ConfigurationAdmin configAdmin;

	@Reference
	ConfigurationUtil configUtil;

	// Note: target filter is on channel ID
	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE, name = "channelHandler")
	void setChannelHandler(ChannelHandler handler, Map<String, Object> props) {

		System.out.println("setting channel handler " + handler);
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

		final Key key = new Key(handlerName, factoryPid);

		if (!deferrers.containsKey(key)) {
			// TODO: log warning
			System.err.println(String.format("Error: key '%s' does not exist in deferred handler map, ignoring", key));
			return;
		}

		// Resolve
		deferrers.get(key).resolve(handler);

	}

	void unsetChannelHandler(ChannelHandler handler, Map<String, Object> props) {
		// try {
		// String handlerName = (String) props.get(Property.ChannelHandler.CHANNEL_ID);
		// referencedHandlers.remove(handlerName, handler);
		// // TODO: log
		// System.out.println(String.format("Removed '%s' handler with properties:\n%s",
		// handlerName, props));
		//
		// } catch (NullPointerException e) {
		// // TODO: log
		// String errMessage = "Error: unset handler '%s' does not have a string
		// property named '%s', ignoring. Is the '%s' target set correctly?";
		// System.err.println(String.format(errMessage, handler.toString(),
		// Property.ChannelHandler.CHANNEL_ID,
		// ReferenceName.OsgiChannelHandler.CHANNEL_HANDLER));
		// }
	}

	// Currently a no-op so as not to unset and then set the reference.
	void updatedChannelHandler(ChannelHandler handler) {
	}

	@Activate
	void activate(Configuration.OsgiChannelHandler config, Map<String, Object> props) {

		System.out.println("Activated dynamic handler properties: " + props);
		assert config.factoryPids().length == config.handlerNames().length;
		this.config = config;

		// Where the magic happens
		//
		// First create the managed service factory configurations
		promises.add(createFactoryConfigurations(config.factoryPids())
				// Then update the configurations with required properties
				.then((p) -> updateConfigsAndMaps(p, config.handlerNames(), config.channelId()))
				// And finally add to the pipeline if not already added
				.then((p) -> maybeAddToPipeline(p, deferredContext.getPromise())));

		// TODO: log
		System.out.println("Activated " + this);

	}

	@Modified
	void modified(Configuration.OsgiChannelHandler config) {
		System.out.println("Modified " + this);
	}

	/*
	 * Create a list of configuration promises
	 */
	private Promise<List<org.osgi.service.cm.Configuration>> createFactoryConfigurations(String[] factoryPids) {
		final Deferred<List<org.osgi.service.cm.Configuration>> results = new Deferred<>();

		executor.execute(new Runnable() {
			@Override
			public void run() {
				results.resolveWith(Promises.all(Arrays.stream(factoryPids).map(p -> createFactoryConfiguration(p))
						.collect(Collectors.toList())));
			}
		});

		return results.getPromise();
	}

	/*
	 * Create a configuration promise
	 * 
	 * Note: could use the Async Service here Refactor opportunity: could be merged
	 * with createFactoryConfigurations
	 */
	private Promise<org.osgi.service.cm.Configuration> createFactoryConfiguration(String factoryPid) {
		final Deferred<org.osgi.service.cm.Configuration> deferred = new Deferred<>();
		try {
			final org.osgi.service.cm.Configuration configuration = configAdmin.createFactoryConfiguration(factoryPid,
					"?");
			deferred.resolve(configuration);
		} catch (Exception e) {
			deferred.fail(e);
		}
		return deferred.getPromise();
	}

	/*
	 * 
	 */
	private Promise<List<ChannelHandler>> updateConfigsAndMaps(Promise<List<org.osgi.service.cm.Configuration>> promisedConfigs,
			String[] handlerNames, String channelId) {

		Deferred<List<ChannelHandler>> result = new Deferred<>();

		executor.execute(new Runnable() {

			@Override
			public void run() {
				try {
					List<org.osgi.service.cm.Configuration> configs = promisedConfigs.getValue();
					final List<Promise<ChannelHandler>> promises = new ArrayList<>(configs.size());
					IntStream.range(0, configs.size()).forEach(i -> {
						final Deferred<ChannelHandler> deferred = new Deferred<>();
						final org.osgi.service.cm.Configuration c = configs.get(i);
						final String handlerName = handlerNames[i];
						final Key key = new Key(handlerName, c.getFactoryPid());

						promises.add(deferred.getPromise());

						// Store configurations for future deletion
						OsgiChannelHandlerProvider.this.configurations.put(key, c);

						// Putting in the concurrent maps needs to happen-before updating the
						// configuration
						deferrers.put(key, deferred);

						// Fail the channel handler defer if its expected configuration cannot be
						// updated
						try {
							c.update(configUtil.toChannelHandlerProps(handlerName, channelId));
						} catch (Exception e) {
							deferred.fail(e);
						}
					});
					result.resolveWith(Promises.all(promises));
				} catch (InvocationTargetException | InterruptedException e) {
					result.fail(e);
				}

			}
		});

		return result.getPromise();
	}

	private Promise<List<Void>> maybeAddToPipeline(Promise<List<ChannelHandler>> promisedHandlers,
			Promise<ChannelHandlerContext> promisedContext) {

		Deferred<List<Void>> result = new Deferred<>();

		executor.execute(new Runnable() {

			@Override
			public void run() {

				try {
					List<ChannelHandler> handlers = promisedHandlers.getValue();
					ChannelHandlerContext context = promisedContext.getValue();
					final List<Promise<Void>> promises = new ArrayList<>();
					handlers.forEach(h -> {

						// Ensure a handler is only added once, via a promise
						if (!addedToPipeline.containsKey(h)) {
							final Deferred<Void> deferred = new Deferred<>();
							try {
								context.pipeline().addLast(h);
								// TODO log
								System.out.println("added to pipeline channel handler " + h);
								deferred.resolve(null);
							} catch (Exception e) {
								deferred.fail(e);
							}
							addedToPipeline.put(h, deferred.getPromise());
						}
						promises.add(addedToPipeline.get(h));
					});
					// Now allow reading of channel
					context.channel().config().setAutoRead(true);
					// and resolve
					result.resolveWith(Promises.all(promises));
				} catch (InvocationTargetException | InterruptedException e) {
					result.fail(e);
				}

			}
		});

		return result.getPromise();
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
	// @Modified
	// void modified(Configuration.OsgiChannelHandler config, Map<String, ?> props)
	// {
	//
	// assert config.factoryPids().length == config.handlerNames().length;
	//
	// Configuration.OsgiChannelHandler priorConfig = this.config;
	// this.config = config;
	//
	// // Get the last promise, either via activate or a previous call to modified
	// // OSGi ensures either of these methods are called sequentially, so
	// forcefully
	// // remove the first promise and chain off of it
	//
	// // Create our sets used for filtering
	// final Set<Key> previous = IntStream.range(0,
	// priorConfig.factoryPids().length)
	// .mapToObj(i -> new Key(priorConfig.handlerNames()[i],
	// priorConfig.factoryPids()[i]))
	// .collect(Collectors.toSet());
	// final Set<Key> modified = IntStream.range(0,
	// priorConfig.factoryPids().length)
	// .mapToObj(i -> new Key(config.handlerNames()[i], config.factoryPids()[i]))
	// .collect(Collectors.toSet());
	//
	// final Set<Key> all = new HashSet<>(previous);
	// all.addAll(modified);
	//
	// final Set<Key> toRemove = new HashSet<>(all);
	// toRemove.removeAll(modified);
	//
	// final Set<Key> toAdd = new HashSet<>(all);
	// toAdd.removeAll(previous);
	//
	// promises.removeFirst().
	// //
	// then(() -> removeHandlers(toRemove,
	// deferredContext.getPromise().getValue()));
	//
	// // // No race condition since OSGi calls the modified method sequentially
	// // // if called multiple times.
	// // this.properties.clear();
	// // this.properties.putAll(props);
	// //
	// // String[] previousHandlers = this.config.factoryPids();
	// // String[] modifiedHandlers = config.factoryPids();
	// // this.config = config;
	// //
	// // activatedHandlers.onResolve(new Runnable() {
	// //
	// // @Override
	// // public void run() {
	// // modifyHandlers(previousHandlers, modifiedHandlers);
	// // }
	// // });
	// //
	// // // TODO: log
	// // System.out.println("Modified " + this);
	//
	// }

	//
	// private Promise<Void> deleteConfiguration(Key key) {
	// final Deferred<Void> deferred = new Deferred<>();
	//
	// return deferred.getPromise();
	// }
	//
	// private Promise<List<Void>> removeHandlers(Collection<Key> keys,
	// ChannelHandlerContext context) {
	//
	// List<Promise<Void>> promises = new ArrayList<>();
	//
	// keys.stream().forEach(k -> {
	// final Deferred<Void> deferred = new Deferred<>();
	// try {
	// context.pipeline().remove(k.handlerName);
	// // TODO: log
	// System.out.println(String.format("Removed handler via modification: %s", h));
	// } catch (Exception e) {
	// deferred.fail(e);
	// }
	// });
	// return Promises.all(promises);
	// }

	/*
	 * Note: being called by OSGi modified method, within a runnable
	 * 
	 * TODO: test me!
	 */
	private void modifyHandlers(String[] previousHandlers, String[] modifiedHandlers) {

		//
		// contextPromise.onResolve(new Runnable() {
		//
		// @Override
		// public void run() {
		// ChannelHandlerContext ctx;
		// try {
		// ctx = contextPromise.getValue();
		//
		// // Remove those handlers from the pipeline which need removal
		// toRemove.forEach(h -> {
		// // TODO: log
		// System.out.println(String.format("Removed handler via modification: %s", h));
		// ctx.pipeline().remove(h);
		// });
		//
		// // Using the current ordered configuration, add only those needing to be
		// added,
		// // maintaining order.
		// IntStream.range(0, modifiedHandlers.length).filter(i ->
		// toAdd.contains(modifiedHandlers[i]))
		// .forEach(i -> {
		// final String handlerName = modifiedHandlers[i];
		// final ChannelHandler handler = referencedHandlers.get(handlerName);
		// // If this is the first handler, add as first. No name needed
		// if (i == 0) {
		// ctx.pipeline().addFirst(handler);
		// // TODO: log
		// System.out.println(
		// String.format("Added handler '%s' first via modification", handlerName));
		// }
		// // Otherwise, use the previous handler, either just added or previously
		// added,
		// // as a reference and add after it.
		// else {
		// String priorHandlerName = modifiedHandlers[i - 1];
		// ctx.pipeline().addAfter(priorHandlerName, handlerName, handler);
		// // TODO: log
		// System.out.println(String.format("Added handler '%s' after '%s' via
		// modification",
		// handlerName, priorHandlerName));
		// }
		// });
		// } catch (InvocationTargetException | InterruptedException e) {
		// // Dunno when this is reachable?
		// e.printStackTrace();
		// }
		// }
		// });

	}

	@Deactivate
	void deactivate(Configuration.OsgiChannelHandler config, Map<String, ?> properties) {
		// TODO: do anything?
		// Called when configuration is removed, bundle is going away, channel is closed
		// (via channel init)
		// TODO: log
		System.out.println("Deactivating " + this);
	}

	//
	// SERVICE METHODS
	//

	@Override
	public void handlerAdded(final ChannelHandlerContext ctx) throws Exception {
		deferredContext.resolve(ctx);
	}

	// TODO: close
	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
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

	@Override
	public String toString() {
		return String.format(
				"OsgiChannelHandlerProvider [appName=%s, channelId=%s, factoryPids=%s, handlerNames=%s, inetHost=%s, inetPort=%d]",
				config.appName(), config.channelId(), Arrays.toString(config.factoryPids()), Arrays.toString(config.handlerNames()), config.inetHost(),
				config.inetPort());
	}

	private static class Key {

		final String handlerName;
		final String factoryPid;

		Key(String handlerName, String factoryPid) {
			super();
			this.handlerName = handlerName;
			this.factoryPid = factoryPid;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((factoryPid == null) ? 0 : factoryPid.hashCode());
			result = prime * result + ((handlerName == null) ? 0 : handlerName.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Key other = (Key) obj;
			if (factoryPid == null) {
				if (other.factoryPid != null)
					return false;
			} else if (!factoryPid.equals(other.factoryPid))
				return false;
			if (handlerName == null) {
				if (other.handlerName != null)
					return false;
			} else if (!handlerName.equals(other.handlerName))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return "Key [handlerName=" + handlerName + ", factoryPid=" + factoryPid + "]";
		}

	}
}
