package io.blesmol.netty.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
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

	// This handler's context
	private final Deferred<ChannelHandlerContext> deferredContext = new Deferred<>();
	private final Promise<ChannelHandlerContext> promisedContext = deferredContext.getPromise();

	// Needs volatile since it can be updated on modified method calls
	// after activate method, possibly on a different thread.
	private volatile Configuration.OsgiChannelHandler config;

	// Deque of activated or modified promises
	private final ConcurrentLinkedDeque<Promise<List<Void>>> promises = new ConcurrentLinkedDeque<>();

	private final ExecutorService executor = Executors.newCachedThreadPool();

	private final Map<Key, Deferred<ChannelHandler>> deferrers = new ConcurrentHashMap<>();
	private final Map<Key, Deferred<org.osgi.service.cm.Configuration>> configurations = new ConcurrentHashMap<>();
	private final Map<ChannelHandler, Promise<Void>> addedToPipeline = new ConcurrentHashMap<>();
	private final Map<Key, Deferred<Key>> deferredKeys = new ConcurrentHashMap<>();

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

		// TODO log
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

		// Resolve handler
		deferrers.get(key).resolve(handler);

	}

	void unsetChannelHandler(ChannelHandler handler, Map<String, Object> props) {

		// TODO log
		System.out.println("unsetting channel handler " + handler);
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

		// Remove previously resolved handler
		deferrers.remove(key);

		// If this handler was added, remove
		if (addedToPipeline.remove(handler) != null) {
			promisedContext.then((p) -> removeFromPipeline(handler, promisedContext.getValue()));

		}

	}

	// Currently a no-op so as not to unset and then set the reference.
	void updatedChannelHandler(ChannelHandler handler) {
	}

	@Activate
	void activate(Configuration.OsgiChannelHandler config, Map<String, Object> props) {

		System.out.println("Activated dynamic handler properties: " + props);
		assert config.factoryPids().length == config.handlerNames().length;
		this.config = config;

		promises.add(
				// First create the managed service factory configurations
				maybeCreateFactoryConfigurations(toKeys(config.handlerNames(), config.factoryPids()))
				// Then update the configurations with required properties
				.then((p) -> maybeUpdateFactoryConfigurations(p.getValue(), config.handlerNames(), config.channelId()))
				// And finally add to the pipeline if not already added
				.then((p) -> maybeAddToPipeline(p.getValue(), promisedContext.getValue()),
						(f) -> f.getFailure().printStackTrace()));

		// TODO: log
		System.out.println("Activated " + this);

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
	 * configuration and prior configuration.
	 * 
	 */
	@Modified
	void modified(Configuration.OsgiChannelHandler config, Map<String, ?> props) {

		assert config.factoryPids().length == config.handlerNames().length;

		Configuration.OsgiChannelHandler priorConfig = this.config;
		this.config = config;

		LinkedHashMap<Key, Deferred<Key>> thesePromisedKeys = IntStream.range(0, config.factoryPids().length)
				.mapToObj(it -> new Key(config.handlerNames()[it], config.factoryPids()[it]))
				.collect(Collectors.toMap(Function.<Key>identity(), k -> new Deferred<Key>(), (u, v) -> {
					throw new IllegalStateException(String.format("Duplicate key %s", u));
				}, LinkedHashMap::new));

		// Create our sets used for filtering
		final Set<Key> priorKeys = IntStream.range(0, priorConfig.factoryPids().length)
				.mapToObj(i -> new Key(priorConfig.handlerNames()[i], priorConfig.factoryPids()[i]))
				.collect(Collectors.toSet());
		final Set<Key> thisKeys = IntStream.range(0, config.factoryPids().length)
				.mapToObj(i -> new Key(config.handlerNames()[i], config.factoryPids()[i])).collect(Collectors.toSet());

		// Union of the above sets
		final Set<Key> allKeys = new HashSet<Key>(priorKeys);
		allKeys.addAll(thisKeys);

		// Remove the keys that don't exist in this config
		final Set<Key> toRemove = allKeys.stream().filter(it -> !thisKeys.contains(it)).collect(Collectors.toSet());

		// Add the keys that exist in this config but not the previous one
		final Set<Key> toAdd = thisKeys.stream().filter(it -> !priorKeys.contains(it)).collect(Collectors.toSet());

		// To add needs to come

		// Get promise created via prior activate or modified method call
		// and chain off of it
		promises.remove().then((p) -> deleteFactoryConfigurations(toRemove));

		System.out.println("Modified " + this);
	}

	/*
	 * Note: being called by OSGi modified method, within a runnable
	 * 
	 * TODO: test me!
	 */
	private void modifyHandlers(String[] previousHandlers, String[] modifiedHandlers) {

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

		System.out.println("Deactivating dynamic handler properties: " + properties);

		// Chain this promise off the existing one in the queue, which there should
		// always be one. What we create we must destroy
		promises.remove()
				.then((p) -> deleteFactoryConfigurations(IntStream.range(0, config.handlerNames().length)
						.mapToObj(i -> new Key(config.handlerNames()[i], config.factoryPids()[i]))
						.collect(Collectors.toList())));

		// TODO: log
		System.out.println("Deactivated " + this);
	}
	//
	// private Promise<List<Key>> createOrModifyConfigurations(List<Key> keys) {
	// final Deferred<List<Key>> results = new Deferred<>();
	//
	// executor.execute(new Runnable() {
	// @Override
	// public void run() {
	// results.resolveWith(Promises.all(Arrays.stream(factoryPids).map(p ->
	// createFactoryConfiguration(p))
	// .collect(Collectors.toList())));
	// }
	// });
	//
	// return results.getPromise();
	// }
	//

	private List<Key> toKeys(String[] handlerNames, String[] factoryPids) {

		assert handlerNames.length == factoryPids.length;

		return IntStream.range(0, handlerNames.length).mapToObj(it -> new Key(handlerNames[it], factoryPids[it]))
				.collect(Collectors.toList());
	}

	/*
	 * Create a list of configuration promises
	 */
	private Promise<List<org.osgi.service.cm.Configuration>> maybeCreateFactoryConfigurations(List<Key> keys) {
		final Deferred<List<org.osgi.service.cm.Configuration>> results = new Deferred<>();

		executor.execute(new Runnable() {
			@Override
			public void run() {
				final List<Promise<org.osgi.service.cm.Configuration>> promisedConfigs = new ArrayList<>();

				keys.stream().forEachOrdered(it -> {

					Deferred<Key> deferredKey = OsgiChannelHandlerProvider.this.deferredKeys.get(it);
					
//					if ()
					
					
					Deferred<org.osgi.service.cm.Configuration> deferred = OsgiChannelHandlerProvider.this.configurations
							.get(it);
					if (deferred == null) {
						deferred = new Deferred<>();
						try {
							final org.osgi.service.cm.Configuration configuration = configAdmin
									.createFactoryConfiguration(it.factoryPid, "?");

							// Store configurations for future deletion
							// This happens-before updating the configuration
							OsgiChannelHandlerProvider.this.configurations.put(it, deferred);

							deferred.resolve(configuration);
						} catch (Exception e) {
							e.printStackTrace();
							deferred.fail(e);
						}
					}
					promisedConfigs.add(deferred.getPromise());
				});

				results.resolveWith(Promises.all(promisedConfigs));
			}
		});

		return results.getPromise();
	}

	/*
	 * 
	 */
	private Promise<List<ChannelHandler>> maybeUpdateFactoryConfigurations(List<org.osgi.service.cm.Configuration> configs,
			String[] handlerNames, String channelId) {

		Deferred<List<ChannelHandler>> result = new Deferred<>();

		executor.execute(new Runnable() {

			@Override
			public void run() {

				final List<Promise<ChannelHandler>> promises = new ArrayList<>(configs.size());
				IntStream.range(0, configs.size()).forEachOrdered(i -> {
					final Deferred<ChannelHandler> deferred = new Deferred<>();
					final org.osgi.service.cm.Configuration c = configs.get(i);
					final String handlerName = handlerNames[i];
					final Key key = new Key(handlerName, c.getFactoryPid());

					promises.add(deferred.getPromise());

					// Putting in the concurrent maps needs to happen-before updating the
					// configuration
					deferrers.put(key, deferred);

					// Fail the channel handler defer if its expected configuration cannot be
					// updated
					try {
						c.update(configUtil.toChannelHandlerProps(handlerName, channelId));
					} catch (Exception e) {
						e.printStackTrace();
						deferred.fail(e);
					}
				});
				System.out.println("updateConfigsAndMaps resolving promise");
				result.resolveWith(Promises.all(promises));

			}
		});

		return result.getPromise();
	}

	private Promise<List<Void>> maybeAddToPipeline(List<ChannelHandler> handlers, ChannelHandlerContext context) {

		Deferred<List<Void>> result = new Deferred<>();
		executor.execute(new Runnable() {

			@Override
			public void run() {

				final List<Promise<Void>> promises = new ArrayList<>();
				handlers.stream().forEachOrdered(h -> {

					// Ensure a handler is only added once, via a promise
					if (!addedToPipeline.containsKey(h)) {
						final Deferred<Void> deferred = new Deferred<>();
						try {
							context.pipeline().addLast(h);
							// TODO log
							System.out.println("added to pipeline channel handler " + h);
							deferred.resolve(null);
						} catch (Exception e) {
							e.printStackTrace();
							deferred.fail(e);
						}
						addedToPipeline.put(h, deferred.getPromise());
					} else {
						System.out.println(String.format("Skipping adding handler '%s' to pipeline ", h));
					}
					promises.add(addedToPipeline.get(h));
				});
				// Now allow reading of channel
				context.channel().config().setAutoRead(true);
				// and resolve
				result.resolveWith(Promises.all(promises));
			}
		});

		return result.getPromise();
	}

	private Promise<Void> removeFromPipeline(ChannelHandler handler, ChannelHandlerContext context) {
		final Deferred<Void> deferred = new Deferred<>();
		try {
			// Only attempt to remove the handler if the channel is still active
			if (context.channel().isActive()) {
				context.pipeline().remove(handler);
			}
			deferred.resolve(null);
		} catch (Exception e) {
			e.printStackTrace();
			deferred.fail(e);
		}
		return deferred.getPromise();
	}

	/*
	 * Parallel delete configurations
	 */
	private Promise<List<Void>> deleteFactoryConfigurations(Collection<Key> keys) {
		final Deferred<List<Void>> results = new Deferred<>();

		executor.execute(new Runnable() {
			@Override
			public void run() {
				List<Promise<Void>> deletedConfigs = new CopyOnWriteArrayList<>();
				keys.parallelStream().forEach((k) -> {
					final Deferred<Void> deferred = new Deferred<>();
					try {
						org.osgi.service.cm.Configuration c = configurations.remove(k).getPromise().getValue();
						c.delete();
						deferred.resolve(null);
					} catch (Exception e) {
						e.printStackTrace();
						deferred.fail(e);
					}
					deletedConfigs.add(deferred.getPromise());
				});
				results.resolveWith(Promises.all(deletedConfigs));
			}
		});

		return results.getPromise();
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
				config.appName(), config.channelId(), Arrays.toString(config.factoryPids()),
				Arrays.toString(config.handlerNames()), config.inetHost(), config.inetPort());
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
