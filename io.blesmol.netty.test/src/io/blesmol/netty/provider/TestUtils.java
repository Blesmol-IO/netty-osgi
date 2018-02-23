package io.blesmol.netty.provider;

import java.util.Dictionary;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.util.tracker.ServiceTracker;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class TestUtils {

	// TODO return trackers and let test cases manage those
	public static <T> ServiceTracker<T, T> getTracker(BundleContext context, Class<T> clazz) throws Exception {
		return getTracker(context, clazz, "");
	}

	public static <T> ServiceTracker<T, T> getTracker(BundleContext context, Class<T> clazz, String filter)
			throws Exception {
		ServiceTracker<T, T> st;
		if (filter != null && !"".equals(filter)) {
			st = new ServiceTracker<>(context, context.createFilter(filter), null);
		} else {
			st = new ServiceTracker<>(context, clazz, null);
		}
		st.open();
		return st;
	}

	public static <T> T getService(BundleContext context, Class<T> clazz, long timeout) throws Exception {
		return getService(context, clazz, timeout, "");
	}

	public static <T> T getService(BundleContext context, Class<T> clazz, long timeout, String filter)
			throws Exception {
		ServiceTracker<T, T> st;
		if (filter != null && !"".equals(filter)) {
			st = new ServiceTracker<>(context, context.createFilter(filter), null);
		} else {
			st = new ServiceTracker<>(context, clazz, null);
		}
		st.open();
		return st.waitForService(timeout);
	}

	public static class TestChannelHandlerFactory implements ManagedServiceFactory {

		protected final BundleContext context;
		protected final Map<String, ServiceRegistration<ChannelHandler>> registrations = new ConcurrentHashMap<>();
		protected final Class<? extends ChannelHandler> channelHandlerClass;
		private final CountDownLatch updatedLatch;
		private final CountDownLatch deletedLatch;

		public TestChannelHandlerFactory(BundleContext context, Class<? extends ChannelHandler> channelHandlerClass) {
			this(context, channelHandlerClass, null, null);
		}
		
		public TestChannelHandlerFactory(BundleContext context, Class<? extends ChannelHandler> channelHandlerClass,
				CountDownLatch updatedLatch, CountDownLatch deletedLatch) {
			super();
			this.context = context;
			this.channelHandlerClass = channelHandlerClass;
			this.updatedLatch = updatedLatch;
			this.deletedLatch = deletedLatch;
		}

		@Override
		public String getName() {
			return TestChannelHandlerFactory.class.getName();
		}

		@Override
		public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
			try {
				ChannelHandler handler = channelHandlerClass.newInstance();				
				registrations.put(pid, context.registerService(ChannelHandler.class, handler, properties));
				if (updatedLatch != null) {
					updatedLatch.countDown();
				}

			} catch (InstantiationException | IllegalAccessException e) {
				throw new ConfigurationException(null, "Could not instantiate class " + channelHandlerClass, e);
			}

		}

		protected ChannelHandler getHandlerViaPid(String pid) {
			ServiceRegistration<ChannelHandler> handlerRegistration = registrations.get(pid);
			if (handlerRegistration != null) {
				return context.getService(handlerRegistration.getReference());
			}
			return null;
		}

		@Override
		public void deleted(String pid) {
			registrations.get(pid).unregister();
			if (deletedLatch != null) {
				deletedLatch.countDown();
			}
		}

	}

	public static class SkeletonChannelHandler implements ChannelHandler {
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

	public static class TestClientHandler extends ChannelInboundHandlerAdapter {

		AtomicBoolean channelActivated = new AtomicBoolean(false);

		@Override
		public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
			if (ctx.channel().isActive()) {
				channelActive(ctx);
			}
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) {
			if (channelActivated.getAndSet(true) == true)
				return;

			System.out.println("Activated client");
			ByteBuf message = Unpooled.buffer(32);
			IntStream.range(0, message.capacity()).forEach(i -> message.writeByte(i));
			ctx.writeAndFlush(message);
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			System.out.println("Client read server response.");
		}

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
			ctx.close();
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			cause.printStackTrace();
			ctx.close();
		}

	}
	
	public static class TestServerHandler extends ChannelInboundHandlerAdapter {

		AtomicBoolean channelActivated = new AtomicBoolean(false);

		@Override
		public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

			if (ctx.channel().isActive()) {
				channelActive(ctx);
			}
		}

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {

			if (channelActivated.getAndSet(true) == true)
				return;

			System.out.println("Test server handler activated");
			super.channelActive(ctx);
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			System.out.println("Test server handler read message, writing back");
			ctx.write(msg);
		}

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) {
			System.out.println("Test server handler flushing");
			ctx.flush();
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			cause.printStackTrace();
			ctx.close();
		}

	}
}
