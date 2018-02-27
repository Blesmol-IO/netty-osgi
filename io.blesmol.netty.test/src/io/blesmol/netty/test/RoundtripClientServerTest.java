package io.blesmol.netty.test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.NettyClient;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.test.TestUtils.TestChannelHandlerFactory;
import io.blesmol.netty.test.TestUtils.TestClientHandler;
import io.blesmol.netty.test.TestUtils.TestServerHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;

@RunWith(MockitoJUnitRunner.class)
public class RoundtripClientServerTest {

	final BundleContext context = FrameworkUtil.getBundle(RoundtripClientServerTest.class).getBundleContext();

	private ConfigurationUtil configUtil;

	private NettyClient client;

	List<String> configPids;

	static final String serverAppName = RoundtripClientServerTest.class.getName() + ":server";
	static final String clientAppName = RoundtripClientServerTest.class.getName() + ":client";
	static final String serverFactoryPid = TestServerHandler.class.getName();
	static final String clientFactoryPid = TestClientHandler.class.getName();
	static final List<String> serverFactoryPids = Arrays.asList(serverFactoryPid);
	static final List<String> clientFactoryPids = Arrays.asList(clientFactoryPid);
	static final String serverHandlerName = "gibson";
	static final String clientHandlerName = "thePlague";
	static final List<String> serverHandlerNames = Arrays.asList(serverHandlerName);
	static final List<String> clientHandlerNames = Arrays.asList(clientHandlerName);
	static final String expectedKey = "hackThePlanet";
	static final String expectedValue = "cookieMonster";

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
		configUtil.deleteConfigurationPids(configPids);
		configUtil = null;
	}

	public static class LatchTestServerHandler extends TestServerHandler implements LatchChannelHandler {
		CountDownLatch latch = null;

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) {
			super.channelReadComplete(ctx);
			if (latch != null)
				latch.countDown();
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
		public void channelActive(ChannelHandlerContext ctx) {
			super.channelActive(ctx);

			if (latch != null) {
				System.out.println("Decrementing latch");
				latch.countDown();
			}
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			super.channelRead(ctx, msg);
			if (latch != null) {
				System.out.println("Decrementing latch");
				latch.countDown();
			}
		}

	}

	@Test
	public void shouldRoundtripMessage() throws Exception {

		// Register server handler factory, which will be called
		Hashtable<String, Object> serverHandlerProps = new Hashtable<>();
		CountDownLatch serverLatch = new CountDownLatch(1);
		RoundtripTestChannelHandlerFactory serverHandlerFactory = new RoundtripTestChannelHandlerFactory(context,
				LatchTestServerHandler.class, serverLatch);
		serverHandlerProps.put(Constants.SERVICE_PID, serverFactoryPid);
		ServiceRegistration<ManagedServiceFactory> serverRegistration = context
				.registerService(ManagedServiceFactory.class, serverHandlerFactory, serverHandlerProps);

		// Register client handler factory, which will be called
		Hashtable<String, Object> clientHandlerProps = new Hashtable<>();
		CountDownLatch clientLatch = new CountDownLatch(2);
		RoundtripTestChannelHandlerFactory clientHandlerFactory = new RoundtripTestChannelHandlerFactory(context,
				LatchTestClientHandler.class, clientLatch);
		clientHandlerProps.put(Constants.SERVICE_PID, clientFactoryPid);
		ServiceRegistration<ManagedServiceFactory> clientRegistration = context
				.registerService(ManagedServiceFactory.class, clientHandlerFactory, clientHandlerProps);

		// Run client and register latch
		final CountDownLatch connectLatch = new CountDownLatch(1);

		// Be quick, get the client! It may disappear shortly
		String clientFilter = String.format("(&(%s=%s)(%s=%s))", Property.NettyClient.APP_NAME, clientAppName,
				Constants.OBJECTCLASS, NettyClient.class.getName());
		client = TestUtils.getService(context, NettyClient.class, 3000, clientFilter);
		client.promise().onResolve(new Runnable() {

			@Override
			public void run() {
				System.out.println("resolved client connection");
				try {
					client.promise().getValue().addListener(new ChannelFutureListener() {
						@Override
						public void operationComplete(ChannelFuture future) throws Exception {
							if (future.isSuccess()) {
								connectLatch.countDown();
							} else {
								throw new RuntimeException(future.cause());
							}
						}
					});
				} catch (InvocationTargetException | InterruptedException e) {
					e.printStackTrace();
					fail();
				}

			}
		});

		// Verify
		assertTrue(connectLatch.await(2, TimeUnit.SECONDS));
		assertTrue(serverLatch.await(3, TimeUnit.SECONDS));
		assertTrue(clientLatch.await(4, TimeUnit.SECONDS));

		// Cleanup
		serverRegistration.unregister();
		clientRegistration.unregister();

	}

	public static interface LatchChannelHandler extends ChannelHandler {
		void setLatch(CountDownLatch latch);
	}

	public static class RoundtripTestChannelHandlerFactory extends TestChannelHandlerFactory {

		private final CountDownLatch latch;

		public RoundtripTestChannelHandlerFactory(BundleContext context,
				Class<? extends ChannelHandler> channelHandlerClass, CountDownLatch latch) {
			super(context, channelHandlerClass);
			this.latch = latch;
		}

		@Override
		public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
			super.updated(pid, properties);
			ChannelHandler handler = getHandlerViaPid(pid);
			if (handler != null && handler instanceof LatchChannelHandler) {
				((LatchChannelHandler) handler).setLatch(latch);
			}
		}
	}
}
