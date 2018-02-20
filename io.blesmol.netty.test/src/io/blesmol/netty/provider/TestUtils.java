package io.blesmol.netty.provider;

import java.util.Dictionary;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.util.tracker.ServiceTracker;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

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

	public static class TestServerHandlerFactory implements ManagedServiceFactory {

		private final BundleContext context;
		private final Map<String, ServiceRegistration<ChannelHandler>> registrations = new ConcurrentHashMap<>();
		private final Class<? extends ChannelHandler> channelHandlerClass;
		private final CountDownLatch updatedLatch;
		private final CountDownLatch deletedLatch;

		public TestServerHandlerFactory(BundleContext context, Class<? extends ChannelHandler> channelHandlerClass) {
			this(context, channelHandlerClass, null, null);
		}

		public TestServerHandlerFactory(BundleContext context, Class<? extends ChannelHandler> channelHandlerClass,
				CountDownLatch updatedLatch, CountDownLatch deletedLatch) {
			super();
			this.context = context;
			this.channelHandlerClass = channelHandlerClass;
			this.updatedLatch = updatedLatch;
			this.deletedLatch = deletedLatch;
		}

		@Override
		public String getName() {
			return TestServerHandlerFactory.class.getName();
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

}
