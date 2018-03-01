package io.blesmol.netty.test;

import java.util.concurrent.CountDownLatch;

import org.osgi.util.promise.Promise;

import io.blesmol.netty.api.DynamicHandlerEvents;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class TestEmbeddedChannelHandler extends ChannelInboundHandlerAdapter {

	final static String HANDLER_NAME = "testEmbeddedChannelHandler";

	final CountDownLatch latch;

	public TestEmbeddedChannelHandler(CountDownLatch latch) {
		super();
		this.latch = latch;
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof Promise<?>) {
			try {
				@SuppressWarnings("unchecked")
				Promise<DynamicHandlerEvents> eventPromise = (Promise<DynamicHandlerEvents>) evt;
				switch (eventPromise.getValue()) {
				case LAST_HANDLER_ADDED:
					latch.countDown();
				default:
					break;

				}
			} catch (ClassCastException e) {

			}
		}
		// relay event
		super.userEventTriggered(ctx, evt);
		ctx.pipeline().remove(this);
	}
}
