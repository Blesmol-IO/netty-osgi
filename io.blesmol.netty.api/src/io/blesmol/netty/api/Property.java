package io.blesmol.netty.api;

// TODO: autogenerate from Configuration interfaces
public interface Property {
	
	/**
	 * Application name
	 */
	String APP_NAME = "appName";

	interface NettyServer {
		String APP_NAME = Property.APP_NAME;
		String INET_HOST = "inetHost";
		String INET_PORT = "inetPort";
		String CHANNEL = "channel";
	}

	interface OsgiChannelHandler extends Property {
		String HANDLERS = "handlers";
	}

	/**
	 * Ordering is checked as follows: before, after, first, or last (default)
	 */
	interface ChannelHandler {
		String APP_NAME = Property.APP_NAME;
		/**
		 * Unique name of handle
		 */
		String HANDLE_NAME = "handleName";

		/**
		 * If not empty, add this handler before the specified handler name
		 */
		String BEFORE = "before";

		/**
		 * If not empty, add this handler after the specified handler name
		 */
		String AFTER = "after";

		/**
		 * If true, add this handler first
		 */
		String FIRST = "first";

		/**
		 * If true, add this handler last (default)
		 */
		String LAST = "last";
	}
	
	interface ChannelInitializer {
		String APP_NAME = Property.APP_NAME;
	}

}
