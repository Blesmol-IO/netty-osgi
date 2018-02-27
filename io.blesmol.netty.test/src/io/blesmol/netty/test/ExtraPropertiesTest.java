package io.blesmol.netty.test;

import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
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
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.test.TestUtils.TestChannelHandlerFactory;
import io.blesmol.netty.test.TestUtils.TestClientHandler;
import io.blesmol.netty.test.TestUtils.TestServerHandler;
import io.netty.channel.ChannelHandler;

@RunWith(MockitoJUnitRunner.class)
public class ExtraPropertiesTest {

	final BundleContext context = FrameworkUtil.getBundle(ExtraPropertiesTest.class).getBundleContext();

	private final ExecutorService executor = Executors.newCachedThreadPool();
	private ConfigurationUtil configUtil;

	List<String> configPids;

	static final String serverAppName = ExtraPropertiesTest.class.getName() + ":server";
	static final String clientAppName = ExtraPropertiesTest.class.getName() + ":client";
	static final String serverFactoryPid = serverAppName;
	static final String clientFactoryPid = clientAppName;
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

		Map<String, Object> extras = new HashMap<>();
		extras.put(expectedKey, expectedValue);

		configPids = configUtil.createNettyServer(serverAppName, "localhost", 54323, serverFactoryPids,
				serverHandlerNames, Optional.of(extras));
		configPids.addAll(configUtil.createNettyClient(clientAppName, "localhost", 54323, clientFactoryPids,
				clientHandlerNames, Optional.of(extras), Optional.empty()));
	}

	@After
	public void after() throws Exception {

		executor.execute(new Runnable() {

			private ConfigurationUtil configUtil = ExtraPropertiesTest.this.configUtil;

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

		configUtil = null;
	}

	static class ExtraTestServerHandler extends TestServerHandler implements ExtraChannelHandler {

	}

	static class ExtraTestClientHandler extends TestClientHandler implements ExtraChannelHandler {

	}

	@Test
	public void shouldHaveExtraProperties() throws Exception {

		final CountDownLatch serverLatch = new CountDownLatch(1);
		final AtomicReference<ServiceRegistration<ManagedServiceFactory>> serverRegistration = new AtomicReference<ServiceRegistration<ManagedServiceFactory>>(
				null);

		// Register server handler factory, which will be called
		executor.execute(new Runnable() {
			@Override
			public void run() {
				Hashtable<String, Object> serverHandlerProps = new Hashtable<>();
				ExtraPropertiesChannelHandlerFactory serverHandlerFactory = new ExtraPropertiesChannelHandlerFactory(
						context, ExtraTestServerHandler.class, expectedKey, expectedValue, serverLatch);
				serverHandlerProps.put(Constants.SERVICE_PID, serverFactoryPid);
				serverRegistration.set(
						context.registerService(ManagedServiceFactory.class, serverHandlerFactory, serverHandlerProps));

			}
		});

		final CountDownLatch clientLatch = new CountDownLatch(1);
		final AtomicReference<ServiceRegistration<ManagedServiceFactory>> clientRegistration = new AtomicReference<ServiceRegistration<ManagedServiceFactory>>(null);
		
		executor.execute(new Runnable() {
			
			@Override
			public void run() {
				Hashtable<String, Object> clientHandlerProps = new Hashtable<>();
				ExtraPropertiesChannelHandlerFactory clientHandlerFactory = new ExtraPropertiesChannelHandlerFactory(context,
						ExtraTestClientHandler.class, expectedKey, expectedValue, clientLatch);
				clientHandlerProps.put(Constants.SERVICE_PID, clientFactoryPid);
				clientRegistration.set(context
						.registerService(ManagedServiceFactory.class, clientHandlerFactory, clientHandlerProps));

				
			}
		});
		// Register client handler factory, which will be called

		// Verify
		assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
		assertTrue(clientLatch.await(5, TimeUnit.SECONDS));

		// Cleanup
		serverRegistration.get().unregister();
		clientRegistration.get().unregister();

	}

	static interface ExtraChannelHandler extends ChannelHandler {
	}

	static class ExtraPropertiesChannelHandlerFactory extends TestChannelHandlerFactory {

		private final String expecetdKey;
		private final String expectedValue;
		private final CountDownLatch latch;

		public ExtraPropertiesChannelHandlerFactory(BundleContext context,
				Class<? extends ChannelHandler> channelHandlerClass, String expectedKey, String expectedValue,
				CountDownLatch latch) {
			super(context, channelHandlerClass);
			this.expecetdKey = expectedKey;
			this.expectedValue = expectedValue;
			this.latch = latch;
		}

		@Override
		public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
			super.updated(pid, properties);
			ChannelHandler handler = getHandlerViaPid(pid);
			if (handler != null && handler instanceof ExtraChannelHandler) {
				String actualValue = (String) properties.get(expecetdKey);
				if (actualValue != null && actualValue.equals(expectedValue)) {
					latch.countDown();
				}
			}
		}
	}
}
