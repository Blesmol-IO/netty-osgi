package io.blesmol.netty.api;

import org.osgi.annotation.versioning.ProviderType;

import io.netty.channel.ChannelFuture;

/**
 * <p>
 * A netty server
 * </p>
 * 
 * @since 0.1
 */
@ProviderType
public interface NettyServer {

	/**
	 * <p>
	 * The channel future returned from the server's bind operation
	 * </p>
	 * 
	 * @return The bind channel future
	 */
	ChannelFuture bindFuture();
}
