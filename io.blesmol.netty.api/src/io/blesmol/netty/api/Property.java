package io.blesmol.netty.api;

@Deprecated
// TODO: auto-generate from Configuration interfaces
public interface Property {

	/**
	 * Application name
	 */
	String APP_NAME = "appName";
	
	String EXTRA_PROPERTIES = "extraProperties";	
	String EXTRA_PREFIX = "extraProperty.";
	
	interface EventLoopGroup {
		String APP_NAME = Property.APP_NAME;
		String GROUP_NAME = "groupName";
	}

	interface EventExecutorGroup {
		String APP_NAME = Property.APP_NAME;
		String GROUP_NAME = EventLoopGroup.GROUP_NAME;
	}
	
	interface HandlerConfig extends Property {
		String INET_HOST = "inetHost";
		String INET_PORT = "inetPort";
		String FACTORY_PIDS = "factoryPids";
		String HANDLER_NAMES = "handlerNames";
	}

	interface NettyServer extends HandlerConfig {
		String CHANNEL = "channel";
	}
	
	interface DynamicChannelHandler extends HandlerConfig {
		String CHANNEL_ID = "channelId";
	}

	interface ChannelHandler {
		String APP_NAME = Property.APP_NAME;
		String CHANNEL_ID = DynamicChannelHandler.CHANNEL_ID;
		String HANDLER_NAME = "handlerName";
	}

	interface ChannelInitializer extends HandlerConfig {
	}

//	interface Bootstrap {
//		String INET_HOST = "inetHost";
//		String INET_PORT = "inetPort";
//		String APP_NAME = Property.APP_NAME;
//		String SERVER_APP_NAME = NettyApi.NettyClient.SERVER_APP_NAME;
//	}
//	
	interface ServerBootstrap {
		String INET_HOST = "inetHost";
		String INET_PORT = "inetPort";
		String APP_NAME = Property.APP_NAME;
	}
}
