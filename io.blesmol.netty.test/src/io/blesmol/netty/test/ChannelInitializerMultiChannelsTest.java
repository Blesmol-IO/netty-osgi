package io.blesmol.netty.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;
import org.osgi.util.tracker.ServiceTracker;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.DynamicChannelHandler;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.test.TestUtils.LatchChannelHandler;
import io.blesmol.netty.test.TestUtils.LatchTestChannelHandlerFactory;
import io.netty.channel.ChannelHandlerContext;
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
	private final static int port = 54326;

	private final static int count = 5;

	private static List<String> configPids = new ArrayList<>();

	private static final BundleContext context = FrameworkUtil.getBundle(ChannelInitializerMultiChannelsTest.class)
			.getBundleContext();
	private static ConfigurationUtil configUtil;

	private static List<ServiceTracker<?, ?>> trackers = new CopyOnWriteArrayList<>();
	private static ServiceRegistration<ManagedServiceFactory> factoryRegistration;
	private static CountDownLatch configurationListenerLatch;
	private static ServiceRegistration<ConfigurationListener> listenerRegistration;
	private static ServiceTracker<ChannelInitializer, ChannelInitializer> initializerTracker;
	private static ChannelInitializer initializer;

	private final static String handlerRootName = "cerealKiller";
	private final static List<String> factoryPids = Stream.of(factoryPid, factoryPid, factoryPid)
			.collect(Collectors.toList());
	private final static List<String> handlerNames = Stream
			.of(handlerRootName + "A", handlerRootName + "B", handlerRootName + "C").collect(Collectors.toList());

	// Expecting count downs equal to count * size of factory / handler lists
	private static final CountDownLatch latch = new CountDownLatch(count * factoryPids.size());

	public static class LatchedChannelInboundHandlerAdapter extends ChannelInboundHandlerAdapter
			implements LatchChannelHandler {

		final Deferred<CountDownLatch> deferredLatch = new Deferred<>();
		final Promise<CountDownLatch> promisedLatch = deferredLatch.getPromise();

		@Override
		public void setLatch(CountDownLatch latch) {
			deferredLatch.resolve(latch);

		}

		@Override
		public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
			promisedLatch.onResolve(new Runnable() {

				@Override
				public void run() {
					try {
						promisedLatch.getValue().countDown();
					} catch (InvocationTargetException | InterruptedException e) {
						e.printStackTrace();
					}
				}
			});
		}
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		ServiceTracker<ConfigurationUtil, ConfigurationUtil> utilTracker = TestUtils.getTracker(context,
				ConfigurationUtil.class);
		trackers.add(utilTracker);
		configUtil = utilTracker.waitForService(250);

		Hashtable<String, Object> factoryProps = new Hashtable<>();

		// Register configuration listiner

		configurationListenerLatch = new CountDownLatch(count * factoryPids.size());
		listenerRegistration = context.registerService(ConfigurationListener.class,
				new TestConfigurationListener(configurationListenerLatch), null);

		// Puposely use a non-sharable handler that also supports latches
		LatchTestChannelHandlerFactory factory = new LatchTestChannelHandlerFactory(context,
				LatchedChannelInboundHandlerAdapter.class, latch);
		factoryProps.put(Constants.SERVICE_PID, factoryPid);
		factoryRegistration = context.registerService(ManagedServiceFactory.class, factory, factoryProps);

		configPids.addAll(configUtil.createChannelInitializer(appName, hostname, port, factoryPids, handlerNames,
				Optional.empty()));

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
		configUtil.deleteConfigurationPids(configPids);
		configUtil = null;
		factoryRegistration.unregister();
		listenerRegistration.unregister();
	}

	private static class TestConfigurationListener extends AbstractTestConfigurationListener {
	
		public TestConfigurationListener(CountDownLatch latch) {
			super(latch);
		}

		@Override
		protected Promise<Boolean> isValidEvent(ConfigurationEvent event) {
			boolean result = event.getFactoryPid().equals(factoryPid) && event.getType() == ConfigurationEvent.CM_UPDATED;
			if (result) {
				System.out.println(String.format("Event match: %s %s %s", event.getFactoryPid(), event.getPid(), event.getType()));
			}
			return Promises.resolved(result);
		}

	}

	@Test
	public void shouldSupportMultipleChannelsViaInitializer() throws Exception {

		// final List<Promise<EmbeddedChannel>> promisedChannels = new ArrayList<>();
		final Map<String, Promise<EmbeddedChannel>> promisedChannels = new ConcurrentHashMap<>();

		// Add services serially
		IntStream.range(0, count).forEach(i -> {
			// executorService.execute(new Runnable() {
			// @Override
			// public void run() {
			// Embedded channels require manually running tasks
			EmbeddedChannel ch = new EmbeddedChannel(DefaultChannelId.newInstance());
			ch.pipeline().addFirst(initializer);
			ch.runPendingTasks();
			System.out.println("Running pending tasks");
			promisedChannels.put(ch.id().asLongText(), Promises.resolved(ch));
			// }
			// });
		});

		// Hacks ahead. Grab all of the dynamic handlers and their embedded channel
		// promises. For each of these, on resolve run pending tasks on the channel.
		// Because embedded channels require their loop to be ran and there's no
		// way to directly poll these tasks so as to run them.

		// First wait until we're configured
		assertTrue(configurationListenerLatch.await(30, TimeUnit.SECONDS));

		// Then, grab all of our dynamic handlers
		String dynamicHandlersFilter = String.format("(&(%s=%s)(%s=%s)(%s=%s)(%s=%d))", Constants.OBJECTCLASS,
				DynamicChannelHandler.class.getName(), Property.DynamicChannelHandler.APP_NAME, appName,
				Property.DynamicChannelHandler.INET_HOST, hostname, Property.DynamicChannelHandler.INET_PORT, port);

		final CountDownLatch configuredLatch = new CountDownLatch(count);
		ServiceTracker<DynamicChannelHandler, DynamicChannelHandler> dynamicHandlersTracker = TestUtils
				.getTracker(context, DynamicChannelHandler.class, dynamicHandlersFilter);

		// There's a race after configuration admin created the handler configs and 
		// the handlers being activated. Sleep here to help win the race
		Thread.sleep(500);
		assertEquals(count, dynamicHandlersTracker.getTracked().size());

		dynamicHandlersTracker.getTracked().entrySet().stream().forEach(es -> {
			String channelId = (String) es.getKey().getProperty(Property.DynamicChannelHandler.CHANNEL_ID);
			System.out.println("Looping on handler " + es.getValue());
			es.getValue().handlersConfigured().onResolve(() -> {
				configuredLatch.countDown();
				final Promise<EmbeddedChannel> promisedChannel = promisedChannels.get(channelId);
				promisedChannel.onResolve(() -> {
					try {
						System.out.println("Ran pending tasks via promise");
						promisedChannel.getValue().runPendingTasks();
					} catch (InvocationTargetException | InterruptedException e) {
						e.printStackTrace();
					}
				});
			});

		});

		// Dynamic handlers should have been configured
		assertTrue(configuredLatch.await(5, TimeUnit.SECONDS));

		// Handlers should have been added
		assertTrue(latch.await(10, TimeUnit.SECONDS));

		Promises.all(promisedChannels.values()).getValue().forEach(c -> {
			// verify all handlers were added
			final List<String> names = c.pipeline().names();
			System.out.println(String.format("Reviewing pipeline %s id %s with names %s", c.pipeline(),
					c.id().asLongText(), names));
			
			int actualSize = c.pipeline().names().size();
			int expectedSize = handlerNames.size() + 2; // dynamic & tail
			
			// Sometimes we catch the outbound handler before it's removed
			// Note: may break if name is changed, sorry :-/
			if (actualSize == expectedSize + 1 && names.contains("dynamicOutboundChannelHandler")) {
				System.out.println("Decrementing acutalSize from " + actualSize);
				actualSize--;
			}
			assertEquals(expectedSize, actualSize);
		});

		promisedChannels.values().forEach(p -> {
			try {
				// Close the channels to free the dynamic handlers
				p.getValue().close();
			} catch (InvocationTargetException | InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		});

	}
}
