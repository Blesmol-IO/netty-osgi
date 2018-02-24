package io.blesmol.netty.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;
import org.osgi.util.tracker.ServiceTracker;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.test.TestUtils.TestChannelHandlerFactory;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.DefaultChannelId;
import io.netty.channel.embedded.EmbeddedChannel;

@SuppressWarnings("rawtypes")
@RunWith(MockitoJUnitRunner.class)
public class ChannelInitializerMultiChannelsTest {

	private final static String appName = ChannelInitializerMultiChannelsTest.class.getName();
	private final static String hostname = "localhost";
	private final static String factoryPid = ChannelInitializerMultiChannelsTest.class.getName();
	private final static int port = 0; // ephemeral
	private static Configuration initializerConfig;

	private static final BundleContext context = FrameworkUtil.getBundle(ChannelInitializerMultiChannelsTest.class)
			.getBundleContext();
	private static ConfigurationUtil configUtil;
	private static ConfigurationAdmin admin;
	private static List<ServiceTracker<?, ?>> trackers = new CopyOnWriteArrayList<>();
	private static ServiceRegistration<ManagedServiceFactory> factoryRegistration;
	private static ServiceTracker<ChannelInitializer, ChannelInitializer> initializerTracker;
	private static ChannelInitializer initializer;
	
	private final static String handlerRootName = "cerealKiller";
	private final static List<String> factoryPids = Stream.of(factoryPid, factoryPid, factoryPid)
			.collect(Collectors.toList());
	private final static List<String> handlerNames = Stream
			.of(handlerRootName + "A", handlerRootName + "B", handlerRootName + "C").collect(Collectors.toList());

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

		initializerConfig = admin.createFactoryConfiguration(io.blesmol.netty.api.Configuration.CHANNEL_INITIALIZER_PID,
				"?");

		Hashtable<String, Object> factoryProps = new Hashtable<>();
		// Puposely use a non-sharable handler
		TestChannelHandlerFactory factory = new TestChannelHandlerFactory(context, ChannelInboundHandlerAdapter.class);
		factoryProps.put(Constants.SERVICE_PID, factoryPid);
		factoryRegistration = context.registerService(ManagedServiceFactory.class, factory, factoryProps);

		initializerConfig
				.update(configUtil.toChannelInitializerProperties(appName, hostname, port, factoryPids, handlerNames, Optional.empty()));

		String filter = String.format("(&(%s=%s)(%s=%s)(%s=%s)(%s=%d))", Constants.OBJECTCLASS,
				ChannelInitializer.class.getName(), Property.ChannelInitializer.APP_NAME, appName,
				Property.ChannelInitializer.INET_HOST, hostname, Property.ChannelInitializer.INET_PORT, port);

		initializerTracker = TestUtils.getTracker(context, ChannelInitializer.class, filter);
		trackers.add(initializerTracker);
		initializer = initializerTracker.waitForService(1000);
		assertNotNull(initializer);

	}

	@AfterClass
	public static void afterClass() throws Exception {
		trackers.forEach(t -> t.close());
		admin = null;
		configUtil = null;
		initializerConfig.delete();
		initializerConfig = null;
		factoryRegistration.unregister();
	}


	@Test
	public void shouldSupportMultipleChannelsViaInitializer() throws Exception {

		final List<Promise<Channel>> promisedChannels = new ArrayList<>(); 
		IntStream.range(0, 10).parallel().forEach(i -> {
			Channel ch = new EmbeddedChannel(DefaultChannelId.newInstance());
			ch.pipeline().addLast(initializer);
			promisedChannels.add(Promises.resolved(ch));
		});

		// let the dust settle
		Thread.sleep(1000);
		Promises.all(promisedChannels).getValue().forEach(c -> {
			// verify all handlers were added
			assertEquals(c.pipeline().names().size(), handlerNames.size() + 2/*dynamic & tail*/);
		});
		
	}
}
