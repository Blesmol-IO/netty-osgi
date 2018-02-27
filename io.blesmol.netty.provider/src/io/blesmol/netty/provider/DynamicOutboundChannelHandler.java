package io.blesmol.netty.provider;

import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

public class DynamicOutboundChannelHandler extends ChannelOutboundHandlerAdapter {

	public static String HANDLER_NAME = "dynamicOutboundChannelHandler";

	private final Deferred<DynamicHandlerEvents> handlerAddedDeferred = new Deferred<>();
	private final Promise<DynamicHandlerEvents> handlerAddedPromise = handlerAddedDeferred.getPromise();

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		handlerAddedDeferred.resolve(DynamicHandlerEvents.LAST_HANDLER_ADDED);
		// Fire the event on the pipeline to get to the first handler
		ctx.channel().pipeline().fireUserEventTriggered(handlerAddedPromise);
		System.out.println("Fired last handler added event in dynamic outbound handler on first context");
		System.out.println("Removing dynamic outbound handler from pipeline");
		ctx.channel().pipeline().remove(this);
//		ctx.executor().execute(new Runnable() {
//			@Override
//			public void run() {
//				System.out.println("Removing dynamic outbound handler from pipeline");
//				ctx.channel().pipeline().remove(DynamicOutboundChannelHandler.this);
//			}
//		});
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		ctx.close();
	}

	@Override
	public void read(ChannelHandlerContext ctx) throws Exception {
		handlerAddedPromise.onResolve(new Runnable() {

			@Override
			public void run() {
				try {
					DynamicOutboundChannelHandler.super.read(ctx);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
		handlerAddedPromise.onResolve(new Runnable() {

			@Override
			public void run() {
				try {
					DynamicOutboundChannelHandler.super.write(ctx, msg, promise);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public void flush(ChannelHandlerContext ctx) throws Exception {
		handlerAddedPromise.onResolve(new Runnable() {

			@Override
			public void run() {
				try {
					DynamicOutboundChannelHandler.super.flush(ctx);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		});
	}

}
