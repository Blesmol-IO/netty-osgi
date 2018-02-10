package io.blesmol.netty.api;

import org.osgi.annotation.versioning.ProviderType;

import io.netty.channel.ChannelFuture;

@ProviderType
public interface NettyClient {

	/**
	 * 
	 * The connect channel future
	 * 
	 * @return a channel future
	 */
	ChannelFuture future();
}
