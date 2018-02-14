package io.blesmol.netty.api;

/**
 * Highly recommended reference names for providers
 * 
 * @see org.osgi.service.component.annotations.Reference
 */
public interface ReferenceName {

	interface OsgiChannelHandler {
		String CHANNEL_HANDLER = "channelHandler.target";		
	}

	interface ChannelInitializer {
		String CHANNEL_HANDLER_FACTORY = "channelHandlerFactory.target";
	}
}
