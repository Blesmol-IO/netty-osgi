package io.blesmol.netty.api;

import org.osgi.annotation.versioning.ProviderType;
import org.osgi.util.promise.Promise;

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
	 * A promise maybe containing the channel future returned from the server's bind operation
	 * </p>
	 * 
	 * @return A promise maybe containing the bind channel future
	 */
	Promise<ChannelFuture> promise();
}
