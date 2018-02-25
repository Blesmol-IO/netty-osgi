package io.blesmol.netty.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
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

	private final Map<String, Object> properties = new ConcurrentHashMap<>();

	// Extra handler properties
	private volatile Optional<Map<String, Object>> extraProperties;

	// Needs volatile since it can be updated on modified method calls
	// after activate method, possibly on a different thread.
	private volatile Configuration.OsgiChannelHandler priorConfig;

	// Deque of activated or modified promises
	private final ConcurrentLinkedDeque<Promise<List<Void>>> promises = new ConcurrentLinkedDeque<>();

	// Holds the defers for channel handlers; resolved asynchronously when a handler
	// is set
	private final Map<Key, Deferred<ChannelHandler>> deferredHandlers = new ConcurrentHashMap<>();
	// Holds promises for configurations generated by configuration admin, resolved
	// when created
	private final Map<Key, Promise<org.osgi.service.cm.Configuration>> configurations = new ConcurrentHashMap<>();
	// Lookup map between keys and handlers
	private final Map<Key, ChannelHandler> keysToHandlers = new ConcurrentHashMap<>();
	// Holds empty promises for keys added to pipeline
	private final Map<Key, Promise<Void>> keyedPipeline = new ConcurrentHashMap<>();

	//
	// OSGI FIELD AND METHOD REFERENCES
	//

	@Reference
	ExecutorService executor;

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
		deferredHandlers.remove(key);

		// If this handler was added, remove
		if (keyedPipeline.remove(key) != null) {
			promisedContext.then((p) -> removeFromPipeline(key, handler, promisedContext.getValue()));

		}

	}

	// Currently a no-op so as not to unset and then set the reference.
	void updatedChannelHandler(ChannelHandler handler) {
	}

	@Activate
	void activate(Configuration.OsgiChannelHandler config, Map<String, Object> props) {

		System.out.println("Activated dynamic handler properties: " + props);
		this.properties.putAll(props);

		// Update extra properties
		extraProperties = configUtil.toOptionalExtraProperties(props);

		this.priorConfig = config;

		final List<Key> keys = toKeys(config.handlerNames(), config.factoryPids());

		addOrUpdateConfigurationsAndHandlers(keys);

		// TODO: log
		System.out.println("Activated " + this);

	}

	@Modified
	void modified(Configuration.OsgiChannelHandler config, Map<String, Object> props) {

		assert config.factoryPids().length == config.handlerNames().length;

		this.properties.clear();
		this.properties.putAll(props);

		// Update extra properties
		extraProperties = configUtil.toOptionalExtraProperties(props);

		Configuration.OsgiChannelHandler priorConfig = this.priorConfig;
		this.priorConfig = config;

		// Create our sets used for filtering
		final Set<Key> theseKeys = IntStream.range(0, config.factoryPids().length)
				.mapToObj(i -> new Key(config.handlerNames()[i], config.factoryPids()[i])).collect(Collectors.toSet());

		// Union of the above sets
		final Set<Key> allKeys = IntStream.range(0, priorConfig.factoryPids().length)
				.mapToObj(i -> new Key(priorConfig.handlerNames()[i], priorConfig.factoryPids()[i]))
				.collect(Collectors.toSet());
		allKeys.addAll(theseKeys);

		// Remove the keys that don't exist in this config
		final Set<Key> toRemove = allKeys.stream().filter(it -> !theseKeys.contains(it)).collect(Collectors.toSet());

		// Get promise created via prior activate or modified method call
		// and chain off of it
		promises.remove()
				// Remove the cruft first
				.then((p) -> deleteFactoryConfigurations(toRemove))
				// Then add / update everything else
				.then((p) -> addOrUpdateConfigurationsAndHandlers(toKeys(config.handlerNames(), config.factoryPids())));

		System.out.println("Modified " + this);
	}

	private Promise<Void> addOrUpdateConfigurationsAndHandlers(final List<Key> keys) {

		promises.add(
				// First create the managed service factory configurations
				maybeCreateFactoryConfigurations(keys)
						// Then update the configurations with required properties
						.then((p) -> maybeUpdateFactoryConfigurations(p.getValue(), keys, priorConfig.channelId()))
						// And finally add to the pipeline if not already added
						.then((p) -> maybeAddToPipeline(p.getValue(), promisedContext.getValue(), keys),
								(f) -> f.getFailure().printStackTrace()));
		return Promises.resolved(null);
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

		priorConfig = config;
		// TODO: log
		System.out.println("Deactivated " + this);
	}

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

					Promise<org.osgi.service.cm.Configuration> promise = configurations.get(it);
					if (promise == null) {
						Deferred<org.osgi.service.cm.Configuration> deferred = new Deferred<>();
						try {
							final org.osgi.service.cm.Configuration configuration = configAdmin
									.createFactoryConfiguration(it.factoryPid, "?");

							// Store configurations for future deletion
							// This happens-before updating the configuration
							OsgiChannelHandlerProvider.this.configurations.put(it, deferred.getPromise());

							deferred.resolve(configuration);
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

		return results.getPromise();
	}

	/*
	 * 
	 */
	private Promise<List<ChannelHandler>> maybeUpdateFactoryConfigurations(
			List<org.osgi.service.cm.Configuration> configs, List<Key> keys, String channelId) {

		Deferred<List<ChannelHandler>> result = new Deferred<>();

		executor.execute(new Runnable() {

			@Override
			public void run() {

				final List<Promise<ChannelHandler>> promises = new ArrayList<>(configs.size());
				IntStream.range(0, configs.size()).forEachOrdered(i -> {

					final Key key = keys.get(i);

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
							c.update(configUtil.toChannelHandlerProps(priorConfig.appName(), handlerName, channelId,
									extraProperties));
						} catch (Exception e) {
							e.printStackTrace();
							deferred.fail(e);
						}
					}
					promises.add(deferred.getPromise());
				});
				System.out.println("updateConfigsAndMaps resolving promise");
				result.resolveWith(Promises.all(promises));

			}
		});

		return result.getPromise();
	}

	private Promise<List<Void>> maybeAddToPipeline(List<ChannelHandler> handlers, ChannelHandlerContext context,
			List<Key> keys) {

		Deferred<List<Void>> result = new Deferred<>();
		executor.execute(new Runnable() {

			@Override
			public void run() {

				final List<Promise<Void>> promises = new ArrayList<>();

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
							// If this is the first one, add after this dynamic handler
							if (idx == 0) {
								context.pipeline().addAfter(OsgiChannelHandler.HANDLER_NAME, handlerName, handler);
								System.out.println(
										String.format("Added handler '%s' after dynamic handler", handlerName));
							}
							// Else, use the previously added (just now or before) handler as a guide
							else {
								String priorHandlerName = keys.get(idx - 1).handlerName;
								context.pipeline().addAfter(priorHandlerName, handlerName, handler);
								System.out.println(
										String.format("Added handler '%s' after '%s'", handlerName, priorHandlerName));
							}
							deferred.resolve(null);
						} catch (Exception e) {
							e.printStackTrace();
							deferred.fail(e);
						}
						promise = deferred.getPromise();
						keyedPipeline.put(it, promise);
					}
					promises.add(promise);
				});

				// Now allow reading of channel
				System.out.println(
						String.format("Enabling auto read on channel id %s", context.channel().id().asLongText()));
				context.channel().config().setAutoRead(true);
				// and resolve
				result.resolveWith(Promises.all(promises));
			}
		});

		return result.getPromise();
	}

	private Promise<Void> removeFromPipeline(Key key, ChannelHandler handler, ChannelHandlerContext context) {
		final Deferred<Void> deferred = new Deferred<>();
		try {
			// Only attempt to remove the handler if the channel is still active
			if (context.channel().isActive()) {
				context.pipeline().remove(handler);
				System.out.println(String.format("Removed handler '%s' from pipeline", key.handlerName));
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
						org.osgi.service.cm.Configuration c = configurations.remove(k).getValue();
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
		System.out.println(this + " added");
		deferredContext.resolve(ctx);
	}

	// TODO: close
	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		System.out.println(this + " removed");
	}

	@Override
	public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
		System.out.println(this + " read");
		super.channelRead(ctx, msg);
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
		System.out.println(this + " read complete");
		super.channelReadComplete(ctx);
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		System.out.println(this + " user event triggered");
		super.userEventTriggered(ctx, evt);
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		System.out.println(this + " activte");
		super.channelActive(ctx);
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {
		System.out.println(this + " inactive");
		super.channelInactive(ctx);
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
		return String.format("Dynamic handler [%s]", this.properties.get(Constants.SERVICE_PID));
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
