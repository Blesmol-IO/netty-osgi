package io.blesmol.netty.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
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
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.tracker.ServiceTracker;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.DynamicChannelHandler;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.test.TestUtils.SkeletonChannelHandler;
import io.blesmol.netty.test.TestUtils.TestChannelHandlerFactory;
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
	private final List<String> factoryPids = IntStream.range(0, count).mapToObj((i) -> factoryPid)
			.collect(Collectors.toList());
	private final List<String> handlerNames = IntStream.range(0, count).mapToObj((i) -> stressTestHandler + i)
			.collect(Collectors.toList());
	private final EmbeddedChannel ch = new EmbeddedChannel(DefaultChannelId.newInstance());
	private final String channelId = ch.id().asLongText();
	
	private CountDownLatch configurationListenerLatch;
	private ServiceRegistration<ConfigurationListener> listenerRegistration;

	private ServiceTracker<DynamicChannelHandler, DynamicChannelHandler> handlerTracker;

	private static class TestConfigurationListener extends AbstractTestConfigurationListener {
		
		private final Promise<String> pidPromise;
		public TestConfigurationListener(Promise<String> pidPromise, CountDownLatch latch) {
			super(latch);
			this.pidPromise = pidPromise;
		}

		@Override
		protected Promise<Boolean> isValidEvent(ConfigurationEvent event) {
			Deferred<Boolean> result = new Deferred<>();
			
			pidPromise.onResolve(() -> {
				String pid;
				try {
					pid = pidPromise.getValue();
					result.resolve(event.getPid().equals(pid) && event.getType() == ConfigurationEvent.CM_UPDATED);
				} catch (InvocationTargetException | InterruptedException e) {
					result.fail(e);
					e.printStackTrace();
				}
			});
			return result.getPromise();
		}

	}

	
	@Before
	public void before() throws Exception {

		configUtil = TestUtils.getService(context, ConfigurationUtil.class, 250);
		configAdmin = TestUtils.getService(context, ConfigurationAdmin.class, 250);


		Deferred<String> pid = new Deferred<>();
		configurationListenerLatch = new CountDownLatch(1);
		listenerRegistration = context.registerService(ConfigurationListener.class,
				new TestConfigurationListener(pid.getPromise(), configurationListenerLatch), null);
		
		String configurationPid = configUtil.createDynamicChannelHandlerConfig(channelId, appName, hostname, port,
				factoryPids, handlerNames, Optional.empty());
		pid.resolve(configurationPid);
		configurationPids.add(configurationPid);
		
		assertTrue(configurationListenerLatch.await(5000, TimeUnit.SECONDS));
		
		String filter = String.format("(&(%s=%s)(%s=%s))", Property.DynamicChannelHandler.APP_NAME, appName, Property.DynamicChannelHandler.CHANNEL_ID, channelId);
		handlerTracker = TestUtils.getTracker(context, DynamicChannelHandler.class, filter);
		dynamicHandler = handlerTracker.getService();
		assertNotNull(dynamicHandler);
	}

	@After
	public void after() {
		handlerTracker.close();
		listenerRegistration.unregister();
		try {
			configUtil.deleteConfigurationPids(configurationPids);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void shouldAddRemoveHandlers() throws Exception {

		// Add dynamic handler to channel.
		// TODO: add 1000 channels and test here
		CountDownLatch embeddedHandlerLatch = new CountDownLatch(1);
		TestEmbeddedChannelHandler pseudo = new TestEmbeddedChannelHandler(embeddedHandlerLatch);
		ch.pipeline().addFirst(TestEmbeddedChannelHandler.HANDLER_NAME, pseudo);
		ch.pipeline().addAfter(TestEmbeddedChannelHandler.HANDLER_NAME, DynamicChannelHandler.HANDLER_NAME, dynamicHandler);

		final CountDownLatch updatedLatch = new CountDownLatch(count);
		final CountDownLatch deletedLatch = new CountDownLatch(count);

		// Register server handler factory, which will be called by the dynamic handler
		Hashtable<String, Object> props = new Hashtable<>();
		TestChannelHandlerFactory factory = new TestChannelHandlerFactory(context, SkeletonChannelHandler.class,
				updatedLatch, deletedLatch);
		props.put(Constants.SERVICE_PID, factoryPid);
		ServiceRegistration<ManagedServiceFactory> sr = context.registerService(ManagedServiceFactory.class, factory,
				props);

		// Wait for stuff
		assertTrue(updatedLatch.await(30, TimeUnit.SECONDS));
		Future<?> future = dynamicHandler.handlersConfigured().getValue();
		future.get(5000, TimeUnit.SECONDS);

		// Allow embedded pipeline tasks to run after all everything is added
		System.out.println("Running pending tasks");
		ch.runPendingTasks();

		// await latch or fail
		assertTrue(embeddedHandlerLatch.await(30, TimeUnit.SECONDS));

		// Delete the config
		configUtil.deleteConfigurationPids(configurationPids);
		assertTrue(deletedLatch.await(60, TimeUnit.SECONDS));
		sr.unregister();
	}

}
