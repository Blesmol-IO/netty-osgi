package io.blesmol.netty.provider;

import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.util.tracker.ServiceTracker;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.OsgiChannelHandler;
import io.blesmol.netty.api.Property;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;

@RunWith(MockitoJUnitRunner.class)
public class OsgiChannelHandlerProviderStressTest {

	public static class TestChannelHandler implements ChannelHandler {
		@Override public void handlerAdded(ChannelHandlerContext ctx) throws Exception {}
		@Override public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {}
		@Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {}
	}

	ConfigurationUtil configUtil;

	private final BundleContext context = FrameworkUtil.getBundle(ConfigurationIntegrationTest.class)
			.getBundleContext();
	private final ConcurrentLinkedQueue<ServiceRegistration<ChannelHandler>> queue = new ConcurrentLinkedQueue<>();
	private OsgiChannelHandler dynamicHandler;
	final String appName = OsgiChannelHandlerProviderStressTest.class.getName();

	// TODO: don't leaks service trackers
	<T> T getService(Class<T> clazz, long timeout) throws Exception {
		return getService(clazz, timeout, "");
	}

	<T> T getService(Class<T> clazz, long timeout, String appName) throws Exception {
		ServiceTracker<T,T> st;
		if (appName != null && !"".equals(appName)) {
			Filter filter = context.createFilter(
				String.format("(&(%s=%s)(appName=%s))", Constants.OBJECTCLASS, clazz.getName(), appName)
			);
			st = new ServiceTracker<>(context, filter, null);
			
		} else {
			st = new ServiceTracker<>(context, clazz, null);
		}
		st.open();
		return st.waitForService(timeout);
	}


	@Before
	public void before() throws Exception {

		configUtil = getService(ConfigurationUtil.class, 700);

		configUtil.createOsgiChannelHandlerConfig(appName, new ArrayList<>());

		dynamicHandler = getService(OsgiChannelHandler.class, 1000, appName);
	}

	@After
	public void after() throws Exception {
		configUtil.deleteOsgiChannelHandlerConfig(appName);
	}

	@Test
	public void shouldAddRemoveHandlers() throws Exception {

		final int count = 1000;
		final int threadPoolSize = 5;

		// Add handlers concurrently
		final ExecutorService registerExecutor = Executors.newFixedThreadPool(threadPoolSize);
		final CountDownLatch registerLatch = new CountDownLatch(count);
		for (long i=count; i>0; i--) {
			final long current = i;
			registerExecutor.execute(new Runnable() {
				public void run() {
					registerChannelHandler(registerLatch, current);
				}
			});
		}

		// Add dynamic handler to channel.
		// TODO: add 1000 channels and test here
		EmbeddedChannel ch = new EmbeddedChannel();
		ch.pipeline().addLast(dynamicHandler);

		// Start a thread that simulates removing handlers
		final ExecutorService unregisterExecutor = Executors.newFixedThreadPool(threadPoolSize);
		final CountDownLatch unregisterLatch = new CountDownLatch(count);
		IntStream.range(0, threadPoolSize)
			.forEach(i -> {
				unregisterExecutor.execute(new Runnable() {
					public void run() {
						unregisterChannelHandler(unregisterLatch);
					}
				});
			});

		// Verify
		assertTrue(registerLatch.await(3, TimeUnit.SECONDS));
		assertTrue(unregisterLatch.await(3, TimeUnit.SECONDS));
	}

	private void registerChannelHandler(CountDownLatch latch, long count) {
		TestChannelHandler service = new TestChannelHandler();
		Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.APP_NAME, appName);
		props.put(Property.ChannelHandler.HANDLE_NAME, service.getClass().getName() + count);

		queue.offer(context.registerService(ChannelHandler.class, service, props));
		latch.countDown();
	}

	private void unregisterChannelHandler(CountDownLatch latch) {
		while (!queue.isEmpty()) {
			ServiceRegistration<ChannelHandler> reg = queue.poll();
			if (reg != null) {
				reg.unregister();
				latch.countDown();
			}
		}
	}
}
