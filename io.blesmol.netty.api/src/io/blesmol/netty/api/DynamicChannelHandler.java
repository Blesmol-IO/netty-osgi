package io.blesmol.netty.api;

import io.netty.channel.ChannelHandler;

public interface DynamicChannelHandler extends ChannelHandler {

	String HANDLER_NAME = "dynamicChannelHandler";
}
