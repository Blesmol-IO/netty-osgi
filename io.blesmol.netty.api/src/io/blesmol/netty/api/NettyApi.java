package io.blesmol.netty.api;

import io.netty.channel.socket.nio.NioSocketChannel;

public interface NettyApi {

	// Common
	String APP_NAME = "appName";

	String INET_HOST = "inetHost";

	String INET_PORT = "inetPort";

	@interface EventLoopGroup {
		String PID = "io.netty.channel.EventLoopGroup";
		String NAME = "nettyEventLoopGroup";

		String APP_NAME = NettyApi.APP_NAME;

		String appName();

		String INET_HOST = NettyApi.INET_HOST;

		String inetHost();

		String INET_PORT = NettyApi.INET_PORT;

		int inetPort();

		String GROUP_NAME = "groupName";

		String groupName();
	}

	@interface EventExecutorGroup {

		String PID = "io.netty.util.concurrent.EventExecutorGroup";
		String NAME = "nettyEventExecutorGroup";

		String APP_NAME = NettyApi.APP_NAME;

		String appName();

		String INET_HOST = NettyApi.INET_HOST;

		String inetHost();

		String INET_PORT = NettyApi.INET_PORT;

		int inetPort();

		String GROUP_NAME = "groupName";

		String groupName();
	}

	@interface NettyClient {

		String PID = "io.blesmol.netty.api.NettyClient";

		String APP_NAME = NettyApi.APP_NAME;

		String appName();

		String INET_HOST = NettyApi.INET_HOST;

		String inetHost();

		String INET_PORT = NettyApi.INET_PORT;

		int inetPort();

		String FACTORY_PIDS = "factoryPids";

		String[] factoryPids();

		String HANDLER_NAMES = "handlerNames";

		String[] handlerNames();

		String CHANNEL = "channel";

		Class<? extends io.netty.channel.Channel> channel() default NioSocketChannel.class;

		// Optional server app name
		String SERVER_APP_NAME = "serverAppName";

		String serverAppName() default "";

		String SHUTDOWN_GROUP = "shutdownGroup";

		boolean shutdownGroup() default true;
	}

	@interface Channel {
		String PID = "io.netty.channel.Channel";

		String APP_NAME = NettyApi.APP_NAME;

		String appName();

		String INET_HOST = NettyApi.INET_HOST;

		String inetHost();

		String INET_PORT = NettyApi.INET_PORT;

		int inetPort();

		String CHANNEL_ID = "channelId";

		String channelId();

	}

}
