package io.blesmol.netty.provider;

import static org.junit.Assert.assertTrue;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ManagedServiceFactory;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.OsgiChannelHandler;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.provider.TestUtils.TestServerHandlerFactory;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.embedded.EmbeddedChannel;

@RunWith(MockitoJUnitRunner.class)
public class OsgiChannelHandlerProviderStressTest {

	public static class TestChannelHandler implements ChannelHandler {
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

	ConfigurationAdmin admin;
	ConfigurationUtil util;
	Configuration dynamicHandlerConfig;

	private final BundleContext context = FrameworkUtil.getBundle(ConfigurationIntegrationTest.class)
			.getBundleContext();

	private OsgiChannelHandler dynamicHandler;
	private final String appName = OsgiChannelHandlerProviderStressTest.class.getName();
	private final String hostname = "localhost";
	private final String factoryPid = OsgiChannelHandlerProviderStressTest.class.getName();
	private final int port = 0; // ephemeral
	private final int count = 500;
	private final List<String> factoryPids = IntStream.range(0, count).mapToObj((i) -> factoryPid).collect(Collectors.toList());
	private final List<String> handlerNames = IntStream.range(0, count).mapToObj((i) -> "stressTestHandler" + i).collect(Collectors.toList());
	private final EmbeddedChannel ch = new EmbeddedChannel();
	private final String channelId = ch.id().asLongText(); // embedded
	
	@Before
	public void before() throws Exception {

		admin = TestUtils.getService(context, ConfigurationAdmin.class, 250);
		util =  TestUtils.getService(context, ConfigurationUtil.class, 250);

		dynamicHandlerConfig = admin
				.createFactoryConfiguration(io.blesmol.netty.api.Configuration.OSGI_CHANNEL_HANDLER_PID, "?");

		// Update the dynamic handler config
		final Dictionary<String, Object> props = util.toDynamicChannelHandlerProperties(channelId, appName, hostname, port, factoryPids, handlerNames);
		dynamicHandlerConfig.update(props);

		String filter = String.format("(&(%s=%s)(%s=%s))",
				//
				Constants.OBJECTCLASS, OsgiChannelHandler.class.getName(),
				Property.OsgiChannelHandler.CHANNEL_ID, channelId);

		dynamicHandler = TestUtils.getService(context, OsgiChannelHandler.class, 3000, filter);
	}

	@After
	public void after() throws Exception {
//		dynamicHandlerConfig.delete();
	}

	@Test
	public void shouldAddRemoveHandlers() throws Exception {


		// Add dynamic handler to channel.
		// TODO: add 1000 channels and test here
		ch.pipeline().addLast(dynamicHandler);

		// 
		final CountDownLatch updatedLatch = new CountDownLatch(count);
		final CountDownLatch deletedLatch = new CountDownLatch(count);

		// Register server handler factory, which will be called by the dynamic handler
		Hashtable<String, Object> props = new Hashtable<>();
		TestServerHandlerFactory factory = new TestServerHandlerFactory(context, TestChannelHandler.class, updatedLatch, deletedLatch);
		props.put(Constants.SERVICE_PID, factoryPid);
		ServiceRegistration<ManagedServiceFactory> sr = context.registerService(ManagedServiceFactory.class, factory, props);

		// Wait for a couple seconds until the dust settles
		assertTrue(updatedLatch.await(4, TimeUnit.SECONDS));

		// Then unregister the factory, which should delete all the created handlers
//		sr.unregister();	
		dynamicHandlerConfig.delete();
		assertTrue(deletedLatch.await(4, TimeUnit.SECONDS));
	}

}
