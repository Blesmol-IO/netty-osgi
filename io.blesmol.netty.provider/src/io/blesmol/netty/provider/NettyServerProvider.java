package io.blesmol.netty.provider;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Dictionary;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.component.annotations.ReferenceScope;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.NettyServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;

@Component(configurationPid = Configuration.NETTY_SERVER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class NettyServerProvider implements NettyServer {

	// Cross-thread access
	private volatile String appName;

	// Guards
	final AtomicBoolean closed = new AtomicBoolean(false);
	final AtomicBoolean channelInitializerUnset = new AtomicBoolean(false);

	// Channel initializer config, set in activate method, does not need to be
	// volatile
	private org.osgi.service.cm.Configuration channelInitializerConfig;

	private final Deferred<ChannelInitializer<? extends Channel>> channelInitializerDeferred = new Deferred<>();
	private final Deferred<ChannelFuture> channelFutureDeferred = new Deferred<>();

	@Reference(scope = ReferenceScope.PROTOTYPE)
	ServerBootstrap server;

	@Reference(scope = ReferenceScope.PROTOTYPE)
	EventLoopGroup bossGroup;

	@Reference(scope = ReferenceScope.PROTOTYPE)
	EventLoopGroup workerGroup;

	@Reference
	ConfigurationAdmin configAdmin;

	@Reference
	ConfigurationUtil configUtil;

	@Reference(scope = ReferenceScope.PROTOTYPE, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.OPTIONAL)
	void setChannelInitializer(ChannelInitializer<? extends Channel> channelInitializer, Map<String, Object> props) {

		// Only set once
		if (!channelInitializerDeferred.getPromise().isDone()) {
			// TODO: log
			System.out
					.println(String.format("Setting channel initializer in '%s' netty server: %s with properties:\n %s",
							appName, channelInitializer, props));
			channelInitializerDeferred.resolve(channelInitializer);
		} else {
			// TODO: log
			System.err.println("Cannot set channel initializer twice in netty server! Ignoring " + channelInitializer);
		}
	}

	// Close if our channel initializer is unset for whatever reason
	void unsetChannelInitializer(ChannelInitializer<? extends Channel> channelInitializer) {
		// TODO: log
		System.out.println(
				String.format("Unsetting channel initializer in '%s' netty server: %s", appName, channelInitializer));
		channelInitializerUnset.set(true);

		// Since the initializer was unset, stop the server
		channelInitializerDeferred.getPromise().onResolve(this::close);
	}

	@Activate
	void activate(Configuration.NettyServer config, Map<String, ?> properties) throws Exception {

		if (closed.get()) {
			// TODO: log
			System.err.println("Reactivating a previously closed netty server is not supported");
			return;
		}

		// Create channel initializer dynamically
		appName = config.appName();
		// TODO: log
		System.out.println(
				String.format("Activating netty server for appName %s and creating channel initializer", appName));

		// Create configuration
		channelInitializerConfig = configAdmin
				.createFactoryConfiguration(io.blesmol.netty.api.Configuration.CHANNEL_INITIALIZER_PID, "?");

		final Dictionary<String, Object> props = configUtil.toChannelInitializerProperties(appName, config.inetHost(),
				config.inetPort(), config.factoryPids(), config.handlerNames());
		channelInitializerConfig.update(props);

		// Then sometime in the future start the server
		final Promise<ChannelInitializer<? extends Channel>> promise = channelInitializerDeferred.getPromise();
		promise.onResolve(() -> {
			try {
				// Start the server
				server.group(bossGroup, workerGroup)
						//
						.channel(config.channel())
						//
						.childHandler(promise.getValue())
						// TODO: consider making configurable
						.option(ChannelOption.SO_BACKLOG, 128)
						// TODO: consider making configurable
						.childOption(ChannelOption.SO_KEEPALIVE, true)
						// disable reading right away until the dynamic handler says it's ok
						.childOption(ChannelOption.AUTO_READ, false);

				// Resolve the deferred channel future
				channelFutureDeferred.resolve(server.bind(config.inetHost(), config.inetPort()));
			} catch (InvocationTargetException | InterruptedException e) {
				// TODO: log
				System.err.println("Error in obtaining channel initializer value via promise");
				e.printStackTrace();
				close();
			}
		});
	}

	@Deactivate
	void deactivate(Configuration.NettyServer config) throws Exception {
		System.out.println("Deactivating netty server for app " + appName);
		channelInitializerDeferred.getPromise().onResolve(this::close);
	}

	@Override
	public Promise<ChannelFuture> promise() {
		return channelFutureDeferred.getPromise();
	}

	void close() {

		// Guard against re-entrance
		if (closed.getAndSet(true) == true) {
			return;
		}

		// TODO: log
		System.out.println("Closing netty server for appName " + appName);

		// Gracefully clean-up
		workerGroup.shutdownGracefully();
		bossGroup.shutdownGracefully();

		// Delete config
		try {
			System.out.println(String.format("Deleting the channel initializer configuration for %s", appName));
			channelInitializerConfig.delete();
		} catch (IllegalStateException | IOException e) {
			if (channelInitializerUnset.get()) {
				// The initializer was unset, which could mean its configuration was already
				// also deleted. In that case, ignore the exception.
			} else {
				// TODO: log
				System.err.println("Error in deleting channel initializer configuration");
				e.printStackTrace();
			}
		}
	}
}
