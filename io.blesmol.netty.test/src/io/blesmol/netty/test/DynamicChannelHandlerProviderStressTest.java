package io.blesmol.netty.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
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
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ManagedServiceFactory;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.DynamicChannelHandler;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.test.TestUtils.SkeletonChannelHandler;
import io.blesmol.netty.test.TestUtils.TestChannelHandlerFactory;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;

@RunWith(MockitoJUnitRunner.class)
public class DynamicChannelHandlerProviderStressTest {

	ConfigurationAdmin configAdmin;
	org.osgi.service.cm.Configuration configuration;
	ConfigurationUtil configUtil;
	private List<String> configurationPids = new ArrayList<>();

	private final BundleContext context = FrameworkUtil.getBundle(DynamicChannelHandlerProviderStressTest.class)
			.getBundleContext();

	private DynamicChannelHandler dynamicHandler;
	private final String appName = DynamicChannelHandlerProviderStressTest.class.getName();
	private final String hostname = "localhost";
	private final String factoryPid = DynamicChannelHandlerProviderStressTest.class.getName();
	private final int port = 0; // ephemeral
	private final int count = 500;
	private final String stressTestHandler = "stressTestHandler";
	private final List<String> factoryPids = IntStream.range(0, count).mapToObj((i) -> factoryPid).collect(Collectors.toList());
	private final List<String> handlerNames = IntStream.range(0, count).mapToObj((i) -> stressTestHandler + i).collect(Collectors.toList());
	private final EmbeddedChannel ch = new EmbeddedChannel(DefaultChannelId.newInstance());
	private final String channelId = ch.id().asLongText();

	@Before
	public void before() throws Exception {

		configUtil =  TestUtils.getService(context, ConfigurationUtil.class, 250);
		configAdmin =  TestUtils.getService(context, ConfigurationAdmin.class, 250);

		// XXX Maybe try out manual configs again? via config admin

//		Dictionary<String, Object> props = configUtil.toDynamicChannelHandlerProperties(channelId, appName, hostname, port, factoryPids, handlerNames, Optional.empty());
//		configuration = configAdmin.createFactoryConfiguration(Configuration.DYNAMIC_CHANNEL_HANDLER_PID, "?");
//		configuration.update(props);
		
//		configurationPids.add(configUtil.createDynamicChannelHandlerConfig(channelId, appName, hostname, port, factoryPids, handlerNames, Optional.empty()));
//		configurationPids.addAll(configUtil.createNettyServer(appName, hostname, port, factoryPids, handlerNames, Optional.empty()));
		configurationPids.addAll(configUtil.createChannelInitializer(appName, hostname, port, factoryPids, handlerNames, Optional.empty()));

		String filter = String.format("(&(%s=%s)(%s=%s)(%s=%d)(%s=%s))",
				//
//				Constants.OBJECTCLASS, DynamicChannelHandler.class.getName(),
				Property.DynamicChannelHandler.CHANNEL_ID, channelId,
				Property.DynamicChannelHandler.INET_HOST, hostname,
				Property.DynamicChannelHandler.INET_PORT, port,
				Property.DynamicChannelHandler.APP_NAME, appName);
//				Property.DynamicChannelHandler.CHANNEL_ID, channelId);

		ChannelInitializer channelInitializer = TestUtils.getService(context, ChannelInitializer.class, 2000);
		ch.pipeline().addFirst(channelInitializer);
		dynamicHandler = TestUtils.getService(context, DynamicChannelHandler.class, 3000);
		assertNotNull(dynamicHandler);
	}

	@Test
	public void shouldAddRemoveHandlers() throws Exception {


		// Add dynamic handler to channel.
		// TODO: add 1000 channels and test here
		ch.pipeline().addFirst(DynamicChannelHandler.HANDLER_NAME, dynamicHandler);

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
		// ignore return value, just using it to wait for service
		TestUtils.getService(context, SkeletonChannelHandler.class, 5000, filter);

		// Delete the config
		configUtil.deleteConfigurationPids(configurationPids);
		assertTrue(deletedLatch.await(10, TimeUnit.SECONDS));
		sr.unregister();
	}

}
