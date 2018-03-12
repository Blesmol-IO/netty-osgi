package io.blesmol.netty.api;

import io.blesmol.netty.api.Property.DynamicChannelHandler;
import io.netty.channel.socket.nio.NioSocketChannel;

public interface NettyApi {

	// Common
	String APP_NAME = "appName";

	String INET_HOST = "inetHost";

	String INET_PORT = "inetPort";
	
	String CHANNEL_ID = "channelId";


	@interface Bootstrap {
		String PID = "io.netty.bootstrap.Bootstrap";

		String APP_NAME = NettyApi.APP_NAME;
		String appName();

		String INET_HOST = NettyApi.INET_HOST;
		String inetHost();

		String INET_PORT = NettyApi.INET_PORT;
		int inetPort();

		String SERVER_APP_NAME = NettyApi.NettyClient.SERVER_APP_NAME;
		String serverAppName() default "";
	}

	
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
	
	@interface ChannelHandler {
		String APP_NAME = NettyApi.APP_NAME;

		String appName();

		String INET_HOST = NettyApi.INET_HOST;

		String inetHost();

		String INET_PORT = NettyApi.INET_PORT;

		int inetPort();

		String CHANNEL_ID = NettyApi.CHANNEL_ID;

		String channelId();

		String HANDLER_NAME = "handlerName";
		
		String handlerName();
	}

	@interface Channel {
		String PID = "io.netty.channel.Channel";

		String APP_NAME = NettyApi.APP_NAME;

		String appName();

		String INET_HOST = NettyApi.INET_HOST;

		String inetHost();

		String INET_PORT = NettyApi.INET_PORT;

		int inetPort();

		String CHANNEL_ID = NettyApi.CHANNEL_ID;

		String channelId();

	}

	@interface ChannelInitializer {
		
		String PID = "io.netty.channel.ChannelInitializer";
		String APP_NAME = NettyApi.APP_NAME;

		String appName();

		String INET_HOST = NettyApi.INET_HOST;

		String inetHost();

		String INET_PORT = NettyApi.INET_PORT;

		int inetPort();

		String FACTORY_PIDS = NettyClient.FACTORY_PIDS;
		String[] factoryPids();

		String HANDLER_NAMES = NettyClient.HANDLER_NAMES;
		String[] handlerNames();
	}

}
