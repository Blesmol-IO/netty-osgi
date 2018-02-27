package io.blesmol.netty.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import org.osgi.service.cm.ManagedServiceFactory;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;
import org.osgi.util.promise.Promises;
import org.osgi.util.tracker.ServiceTracker;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.test.TestUtils.LatchChannelHandler;
import io.blesmol.netty.test.TestUtils.LatchTestChannelHandlerFactory;
import io.blesmol.netty.test.TestUtils.TestChannelHandlerFactory;
import io.netty.channel.Channel;
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
	private static ServiceTracker<ChannelInitializer, ChannelInitializer> initializerTracker;
	private static ChannelInitializer initializer;

	private final static String handlerRootName = "cerealKiller";
	private final static List<String> factoryPids = Stream.of(factoryPid, factoryPid, factoryPid)
			.collect(Collectors.toList());
	private final static List<String> handlerNames = Stream
			.of(handlerRootName + "A", handlerRootName + "B", handlerRootName + "C").collect(Collectors.toList());

	// Expecting count downs equal to count * size of factory / handler lists
	private static final CountDownLatch latch = new CountDownLatch(count * factoryPids.size());
	private static final ExecutorService executorService = Executors.newCachedThreadPool();

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
		executorService.execute(new Runnable() {
			@Override
			public void run() {
				trackers.forEach(t -> t.close());
			}
		});

		executorService.execute(new Runnable() {

			private ConfigurationUtil configUtil = ChannelInitializerMultiChannelsTest.configUtil;

			@Override
			public void run() {
				try {
					configUtil.deleteConfigurationPids(configPids);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					configUtil = null;
				}
			}
		});

		executorService.execute(new Runnable() {
			@Override
			public void run() {
				factoryRegistration.unregister();
			}
		});

		configUtil = null;
	}

	@Test
	public void shouldSupportMultipleChannelsViaInitializer() throws Exception {

		final List<Promise<Channel>> promisedChannels = new ArrayList<>();

		// Add services concurrently
		IntStream.range(0, count).forEach(i -> {
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					Channel ch = new EmbeddedChannel(DefaultChannelId.newInstance());
					ch.pipeline().addFirst(initializer);
					promisedChannels.add(Promises.resolved(ch));
				}
			});
		});

		// let the dust settle
		assertTrue(latch.await(10, TimeUnit.SECONDS));

		Promises.all(promisedChannels).getValue().forEach(c -> {
			// verify all handlers were added
			System.out.println(String.format("Reviewing pipeline %s id %s with names %s", c.pipeline(),
					c.id().asLongText(), c.pipeline().names()));
			assertEquals(c.pipeline().names().size(), handlerNames.size() + 2/* dynamic & tail */);
		});

		promisedChannels.forEach(p -> {
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
