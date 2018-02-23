package io.blesmol.netty.provider;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceScope;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.NettyServer;
import io.blesmol.netty.api.ReferenceName;
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

	private final Deferred<ChannelFuture> channelFutureDeferred = new Deferred<>();

	@Reference(scope = ReferenceScope.PROTOTYPE)
	ServerBootstrap server;

	@Reference(name = ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP)
	EventLoopGroup bossGroup;

	@Reference(name = ReferenceName.NettyServer.WORKER_EVENT_LOOP_GROUP)
	EventLoopGroup workerGroup;

	@Reference
	ConfigurationAdmin configAdmin;

	@Reference
	ConfigurationUtil configUtil;

	@Reference(name = ReferenceName.NettyServer.CHANNEL_INITIALIZER)
	ChannelInitializer<Channel> channelInitializer;

	@Activate
	void activate(Configuration.NettyServer config, Map<String, Object> properties) throws Exception {

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


		server.group(bossGroup, workerGroup)
				//
				.channel(config.channel())
				//
				.childHandler(channelInitializer)
				// TODO: consider making configurable
				.option(ChannelOption.SO_BACKLOG, 128)
				// TODO: consider making configurable
				.childOption(ChannelOption.SO_KEEPALIVE, true)
				// disable reading right away until the dynamic handler says it's ok
				.childOption(ChannelOption.AUTO_READ, false);

		// Resolve the deferred channel future
		channelFutureDeferred.resolve(server.bind(config.inetHost(), config.inetPort()));
	}

	@Deactivate
	void deactivate(Configuration.NettyServer config) throws Exception {
		System.out.println("Deactivating netty server for app " + appName);
		close();
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
	}
}
