package io.blesmol.netty.test;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ManagedServiceFactory;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.test.TestUtils.LatchChannelHandler;
import io.blesmol.netty.test.TestUtils.LatchTestChannelHandlerFactory;
import io.blesmol.netty.test.TestUtils.TestClientHandler;
import io.blesmol.netty.test.TestUtils.TestServerHandler;
import io.netty.channel.ChannelHandlerContext;

@RunWith(MockitoJUnitRunner.class)
public class RoundtripClientServerTest {

	final BundleContext context = FrameworkUtil.getBundle(RoundtripClientServerTest.class).getBundleContext();

	final ExecutorService executorService = Executors.newCachedThreadPool();

	private ConfigurationUtil configUtil;

	List<String> configPids;

	static final String serverAppName = RoundtripClientServerTest.class.getName() + ":server";
	static final String clientAppName = RoundtripClientServerTest.class.getName() + ":client";
	static final String serverFactoryPid = serverAppName;
	static final String clientFactoryPid = clientAppName;
	static final List<String> serverFactoryPids = Arrays.asList(serverFactoryPid);
	static final List<String> clientFactoryPids = Arrays.asList(clientFactoryPid);
	static final String serverHandlerName = "serverHandler";
	static final String clientHandlerName = "clientHandler";
	static final List<String> serverHandlerNames = Arrays.asList(serverHandlerName);
	static final List<String> clientHandlerNames = Arrays.asList(clientHandlerName);

	@Before
	public void before() throws Exception {
		configUtil = TestUtils.getService(context, ConfigurationUtil.class, 700);

		configPids = configUtil.createNettyServer(serverAppName, "localhost", 54321, serverFactoryPids,
				serverHandlerNames, Optional.empty());
		configPids.addAll(configUtil.createNettyClient(clientAppName, "localhost", 54321, clientFactoryPids,
				clientHandlerNames, Optional.empty(), Optional.empty()));
	}

	@After
	public void after() throws Exception {
		try {
			configUtil.deleteConfigurationPids(configPids);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static class LatchTestServerHandler extends TestServerHandler implements LatchChannelHandler {
		CountDownLatch latch = null;

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) {
			super.channelReadComplete(ctx);
			if (latch != null) {
				latch.countDown();
				System.out.println("Decremented server latch in channel read");
			}

		}

		@Override
		public void setLatch(CountDownLatch latch) {
			this.latch = latch;

		}

	}

	public static class LatchTestClientHandler extends TestClientHandler implements LatchChannelHandler {

		CountDownLatch latch = null;

		@Override
		public void setLatch(CountDownLatch latch) {
			this.latch = latch;

		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			super.channelRead(ctx, msg);
			if (latch != null) {
				System.out.println("Decrementing client latch");
				latch.countDown();
			}
		}

	}

	@Test
	public void shouldRoundtripMessage() throws Exception {

		final AtomicReference<ServiceRegistration<ManagedServiceFactory>> serverRegistration = new AtomicReference<ServiceRegistration<ManagedServiceFactory>>(
				null);
		final CountDownLatch serverLatch = new CountDownLatch(1);

		executorService.execute(new Runnable() {

			@Override
			public void run() {
				Hashtable<String, Object> serverHandlerProps = new Hashtable<>();
				LatchTestChannelHandlerFactory serverHandlerFactory = new LatchTestChannelHandlerFactory(context,
						LatchTestServerHandler.class, serverLatch);
				serverHandlerProps.put(Constants.SERVICE_PID, serverFactoryPid);
				serverRegistration.set(
						context.registerService(ManagedServiceFactory.class, serverHandlerFactory, serverHandlerProps));
			}
		});

		final AtomicReference<ServiceRegistration<ManagedServiceFactory>> clientRegistration = new AtomicReference<ServiceRegistration<ManagedServiceFactory>>(
				null);
		final CountDownLatch clientLatch = new CountDownLatch(1);

		// Register client handler factory, which will be called
		executorService.execute(new Runnable() {

			@Override
			public void run() {
				Hashtable<String, Object> clientHandlerProps = new Hashtable<>();
				LatchTestChannelHandlerFactory clientHandlerFactory = new LatchTestChannelHandlerFactory(context,
						LatchTestClientHandler.class, clientLatch);
				clientHandlerProps.put(Constants.SERVICE_PID, clientFactoryPid);
				clientRegistration.set(
						context.registerService(ManagedServiceFactory.class, clientHandlerFactory, clientHandlerProps));

			}
		});


		// // Verify
		assertTrue(serverLatch.await(30, TimeUnit.SECONDS));
		assertTrue(clientLatch.await(30, TimeUnit.SECONDS));

		// Cleanup
		serverRegistration.get().unregister();
		clientRegistration.get().unregister();

	}

}
