package io.blesmol.netty.api;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

public interface OsgiChannelHandler extends ChannelHandler {

	class NoopHandler implements ChannelHandler {

		@Override
		public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		}

		@Override
		public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		}

	}

}
