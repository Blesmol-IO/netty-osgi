package io.blesmol.netty.api;

// TODO: auto-generate from Configuration interfaces
public interface Property {

	/**
	 * Application name
	 */
	String APP_NAME = "appName";

	interface HandlerConfig extends Property {
		String INET_HOST = "inetHost";
		String INET_PORT = "inetPort";
		String FACTORY_PIDS = "factoryPids";
		String HANDLER_NAMES = "handlerNames";
	}

	interface NettyServer extends HandlerConfig {
		String CHANNEL = "channel";
	}

	interface OsgiChannelHandler extends HandlerConfig {
		String CHANNEL_ID = "channelId";
	}

	interface ChannelHandler {
		String CHANNEL_ID = OsgiChannelHandler.CHANNEL_ID;
		String HANDLER_NAME = "handlerName";
	}

	interface ChannelInitializer extends HandlerConfig {
	}

}
