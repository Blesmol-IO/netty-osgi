package io.blesmol.netty.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
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
import org.osgi.util.tracker.ServiceTracker;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.OsgiChannelHandler;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.provider.TestUtils.SkeletonChannelHandler;
import io.blesmol.netty.provider.TestUtils.TestChannelHandlerFactory;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;

@RunWith(MockitoJUnitRunner.class)
public class ModifiedChannelHandlerTest {

	private static final BundleContext context = FrameworkUtil.getBundle(ConfigurationIntegrationTest.class)
			.getBundleContext();

	private static ConfigurationUtil configUtil;
	private static ConfigurationAdmin admin;
	private static Configuration handlerConfig;
	private static OsgiChannelHandler dynamicHandler;
	private static List<ServiceTracker<?, ?>> trackers = new CopyOnWriteArrayList<>();
	private static ServiceTracker<OsgiChannelHandler, OsgiChannelHandler> handlerTracker;
	private static TestChannelHandlerFactory factory;
	private static ServiceRegistration<ManagedServiceFactory> factoryRegistration;

	private final static String appName = ModifiedChannelHandlerTest.class.getName();
	private final static String hostname = "localhost";
	private final static String factoryPid = ModifiedChannelHandlerTest.class.getName();
	private final static int port = 0; // ephemeral

	private final static String handlerRootName = "lordNikon";
	private final static List<String> initialFactoryPids = Stream.of(factoryPid, factoryPid, factoryPid)
			.collect(Collectors.toList());
	private final static List<String> initialHandlerNames = Stream
			.of(handlerRootName + "A", handlerRootName + "B", handlerRootName + "C").collect(Collectors.toList());

	private final static EmbeddedChannel ch = new EmbeddedChannel(DefaultChannelId.newInstance());
	private final static String channelId = ch.id().asLongText();

	private static Dictionary<String, Object> defaultHandlerProps;

	@BeforeClass
	public static void beforeClass() throws Exception {
		ServiceTracker<ConfigurationAdmin, ConfigurationAdmin> adminTracker = TestUtils.getTracker(context,
				ConfigurationAdmin.class);
		trackers.add(adminTracker);
		admin = adminTracker.waitForService(250);

		ServiceTracker<ConfigurationUtil, ConfigurationUtil> utilTracker = TestUtils.getTracker(context,
				ConfigurationUtil.class);
		trackers.add(utilTracker);
		configUtil = utilTracker.waitForService(250);

		handlerConfig = admin.createFactoryConfiguration(io.blesmol.netty.api.Configuration.OSGI_CHANNEL_HANDLER_PID,
				"?");

		String filter = String.format("(&(%s=%s)(%s=%s)(%s=%s))", Constants.OBJECTCLASS,
				OsgiChannelHandler.class.getName(), Property.OsgiChannelHandler.CHANNEL_ID, channelId,
				Property.OsgiChannelHandler.APP_NAME, appName);

		handlerTracker = TestUtils.getTracker(context, OsgiChannelHandler.class, filter);
		trackers.add(handlerTracker);

		Hashtable<String, Object> props = new Hashtable<>();
		factory = new TestChannelHandlerFactory(context, SkeletonChannelHandler.class);
		props.put(Constants.SERVICE_PID, factoryPid);
		factoryRegistration = context.registerService(ManagedServiceFactory.class, factory, props);

		defaultHandlerProps = configUtil.toDynamicChannelHandlerProperties(channelId, appName, hostname, port,
				initialFactoryPids, initialHandlerNames, Optional.empty());

		handlerConfig.update(defaultHandlerProps);
		dynamicHandler = handlerTracker.waitForService(1000);
		assertNotNull(dynamicHandler);
		// Simulate channel initializer
		ch.pipeline().addFirst(OsgiChannelHandler.HANDLER_NAME, dynamicHandler);

	}

	@AfterClass
	public static void afterClass() throws Exception {
		trackers.forEach(t -> t.close());
		admin = null;
		configUtil = null;
		handlerConfig = null;
		factoryRegistration.unregister();
	}

	@After
	public void after() throws Exception {
		handlerConfig.update(defaultHandlerProps);
		Thread.sleep(1000);
	}

	@Test
	public void shouldAddHandlersToConfig() throws Exception {

		List<String> factoryPids = Stream.of(factoryPid, factoryPid, factoryPid, factoryPid, factoryPid, factoryPid)
				.collect(Collectors.toList());
		List<String> handlerNames = Stream.of(handlerRootName + "0", handlerRootName + "A", handlerRootName + "B",
				handlerRootName + "B2", handlerRootName + "C", handlerRootName + "D").collect(Collectors.toList());
		final Dictionary<String, Object> props = configUtil.toDynamicChannelHandlerProperties(channelId, appName,
				hostname, port, factoryPids, handlerNames, Optional.empty());
		handlerConfig.update(props);

		// wait for the dust to settle
		Thread.sleep(1000);
		List<String> pipelineNames = ch.pipeline().names();
		final int idx = pipelineNames.indexOf(handlerNames.get(0));

		// Verify size is correct
		assertEquals(pipelineNames.size(), 2 + handlerNames.size());

		// Verify that each expected handler name is, as ordered, in
		IntStream.range(idx, handlerNames.size() + idx).forEach(i -> {
			assertEquals(handlerNames.get(i - idx), pipelineNames.get(i));
		});

	}

	@Test
	public void shouldRemoveHandlersFromConfig() throws Exception {

		List<String> factoryPids = Stream.of(factoryPid, factoryPid).collect(Collectors.toList());
		List<String> handlerNames = Stream.of(handlerRootName + "B", handlerRootName + "B2")
				.collect(Collectors.toList());
		final Dictionary<String, Object> props = configUtil.toDynamicChannelHandlerProperties(channelId, appName,
				hostname, port, factoryPids, handlerNames, Optional.empty());
		handlerConfig.update(props);

		// wait for the dust to settle
		Thread.sleep(1000);
		List<String> pipelineNames = ch.pipeline().names();
		final int idx = pipelineNames.indexOf(handlerNames.get(0));

		// Verify size is correct
		assertEquals(pipelineNames.size(), 2 + handlerNames.size());

		// Verify that each expected handler name is, as ordered, in the actual pipeline
		IntStream.range(idx, handlerNames.size() + idx).forEach(i -> {
			assertEquals(handlerNames.get(i - idx), pipelineNames.get(i));
		});

	}

	@Test
	public void shouldEmptyConfig() throws Exception {

		List<String> factoryPids = new ArrayList<>();
		List<String> handlerNames = new ArrayList<>();
		final Dictionary<String, Object> props = configUtil.toDynamicChannelHandlerProperties(channelId, appName,
				hostname, port, factoryPids, handlerNames, Optional.empty());
		handlerConfig.update(props);

		// wait for the dust to settle
		Thread.sleep(1000);

		List<String> pipelineNames = ch.pipeline().names();

		// Verify size is correct (dynamic handler and tail)
		assertEquals(pipelineNames.size(), 2);

	}

}
