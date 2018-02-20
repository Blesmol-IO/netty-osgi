package io.blesmol.netty.api;

import io.netty.channel.ChannelHandler;

public interface OsgiChannelHandler extends ChannelHandler {

	String HANDLER_NAME = "dynamicHandler";
}
