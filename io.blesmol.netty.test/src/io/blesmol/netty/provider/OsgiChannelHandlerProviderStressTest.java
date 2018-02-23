package io.blesmol.netty.provider;

import static org.junit.Assert.assertTrue;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
import io.blesmol.netty.provider.TestUtils.SkeletonChannelHandler;
import io.blesmol.netty.provider.TestUtils.TestChannelHandlerFactory;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;

@RunWith(MockitoJUnitRunner.class)
public class OsgiChannelHandlerProviderStressTest {

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
	private final String stressTestHandler = "stressTestHandler";
	private final List<String> factoryPids = IntStream.range(0, count).mapToObj((i) -> factoryPid).collect(Collectors.toList());
	private final List<String> handlerNames = IntStream.range(0, count).mapToObj((i) -> stressTestHandler + i).collect(Collectors.toList());
	private final EmbeddedChannel ch = new EmbeddedChannel(DefaultChannelId.newInstance());
	private final String channelId = ch.id().asLongText();

	@Before
	public void before() throws Exception {

		admin = TestUtils.getService(context, ConfigurationAdmin.class, 250);
		util =  TestUtils.getService(context, ConfigurationUtil.class, 250);

		dynamicHandlerConfig = admin
				.createFactoryConfiguration(io.blesmol.netty.api.Configuration.OSGI_CHANNEL_HANDLER_PID, "?");

		// Update the dynamic handler config
		final Dictionary<String, Object> props = util.toDynamicChannelHandlerProperties(channelId, appName, hostname, port, factoryPids, handlerNames, Optional.empty());
		dynamicHandlerConfig.update(props);

		String filter = String.format("(&(%s=%s)(%s=%s))",
				//
				Constants.OBJECTCLASS, OsgiChannelHandler.class.getName(),
				Property.OsgiChannelHandler.CHANNEL_ID, channelId);

		dynamicHandler = TestUtils.getService(context, OsgiChannelHandler.class, 3000, filter);
	}

	@Test
	public void shouldAddRemoveHandlers() throws Exception {


		// Add dynamic handler to channel.
		// TODO: add 1000 channels and test here
		ch.pipeline().addLast(OsgiChannelHandler.HANDLER_NAME, dynamicHandler);

		// 
		final CountDownLatch updatedLatch = new CountDownLatch(count);
		final CountDownLatch deletedLatch = new CountDownLatch(count);

		// Register server handler factory, which will be called by the dynamic handler
		Hashtable<String, Object> props = new Hashtable<>();
		TestChannelHandlerFactory factory = new TestChannelHandlerFactory(context, SkeletonChannelHandler.class, updatedLatch, deletedLatch);
		props.put(Constants.SERVICE_PID, factoryPid);
		ServiceRegistration<ManagedServiceFactory> sr = context.registerService(ManagedServiceFactory.class, factory, props);

		// Wait for a couple seconds until the dust settles
		assertTrue(updatedLatch.await(5, TimeUnit.SECONDS));

		// Wait until the specified one is found, else 
		// we might delete all before adding
		String filter = String.format("(&(%s=%s)(%s=%s)(%s=%s))",
				//
				Constants.OBJECTCLASS, SkeletonChannelHandler.class.getName(),
				Property.ChannelHandler.HANDLER_NAME, String.format("%s%s", stressTestHandler, count/200),
				ConfigurationAdmin.SERVICE_FACTORYPID, factoryPid
		);
		System.out.println("Got service");

		TestUtils.getService(context, SkeletonChannelHandler.class, 5000, filter);

		// Delete the config
		dynamicHandlerConfig.delete();
		assertTrue(deletedLatch.await(10, TimeUnit.SECONDS));
		sr.unregister();
	}

}
