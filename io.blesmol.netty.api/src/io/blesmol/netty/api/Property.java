package io.blesmol.netty.api;

// TODO: auto-generate from Configuration interfaces
public interface Property {
	
	/**
	 * Application name
	 */
	String APP_NAME = "appName";

	interface NettyServer extends Property {;
		String INET_HOST = "inetHost";
		String INET_PORT = "inetPort";
		String CHANNEL = "channel";
	}

	interface OsgiChannelHandler extends Property {
		String HANDLERS = "handlers";
	}

	interface ChannelHandler extends Property {
		String HANDLE_NAME = "handleName";
	}
	
	interface ChannelInitializer extends Property {
	}

}
