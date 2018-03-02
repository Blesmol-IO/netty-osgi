package io.blesmol.netty.api;

import io.netty.channel.ServerChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

/**
 * <p>
 * Common component property types and configuration PIDs
 * <p>
 *
 */
public interface Configuration {

	String NETTY_SERVER_PID = "io.blesmol.netty.api.NettyServer";

	@interface NettyServer {
		String appName();

		String inetHost();

		int inetPort();

		String[] factoryPids();

		String[] handlerNames();

		Class<? extends ServerChannel> channel() default NioServerSocketChannel.class;
	}

	String DYNAMIC_CHANNEL_HANDLER_PID = "io.blesmol.netty.api.DynamicChannelHandler";

	@interface DynamicChannelHandler {
		String appName();

		String channelId();

		String inetHost();

		int inetPort();

		String[] factoryPids();

		String[] handlerNames();

	}

	String CHANNEL_INITIALIZER_PID = "io.netty.channel.ChannelInitializer";

	@interface ChannelInitializer {
		String appName();

		String inetHost();

		int inetPort();

		String[] factoryPids();

		String[] handlerNames();
	}

	String BOOTSTRAP_PID = "io.netty.bootstrap.Bootstrap";

	@interface Bootstrap {
		String appName();

		String inetHost();

		int inetPort();

		// default empty
		String serverAppName() default "";

	}

	String SERVER_BOOTSTRAP_PID = "io.netty.bootstrap.ServerBootstrap";

	@interface ServerBootstrap {
		String appName();

		String inetHost();

		int inetPort();
	}
}
