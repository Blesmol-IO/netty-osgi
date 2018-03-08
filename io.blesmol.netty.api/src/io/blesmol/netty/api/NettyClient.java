package io.blesmol.netty.api;

import org.osgi.annotation.versioning.ProviderType;
import org.osgi.util.promise.Promise;

import io.netty.channel.ChannelFuture;

@ProviderType
public interface NettyClient {

	/**
	 * 
	 * A promise for a connect channel future
	 * 
	 * @return a promised channel future
	 */
	@Deprecated
	Promise<ChannelFuture> promise();
}
