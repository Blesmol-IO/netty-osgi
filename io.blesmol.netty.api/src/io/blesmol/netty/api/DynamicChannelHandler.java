package io.blesmol.netty.api;

import java.util.concurrent.Future;

import org.osgi.util.promise.Promise;

import io.netty.channel.ChannelHandler;

public interface DynamicChannelHandler extends ChannelHandler {

	String HANDLER_NAME = "dynamicChannelHandler";

	// FIXME: replace with event admin
	@Deprecated
	Promise<Future<?>> handlersConfigured();
}
