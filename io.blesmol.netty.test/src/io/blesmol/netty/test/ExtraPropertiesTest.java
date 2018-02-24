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
import io.blesmol.netty.test.TestUtils.TestChannelHandlerFactory;
import io.blesmol.netty.test.TestUtils.TestClientHandler;
import io.blesmol.netty.test.TestUtils.TestServerHandler;
import io.netty.channel.ChannelHandler;

@RunWith(MockitoJUnitRunner.class)
public class ExtraPropertiesTest {

	final BundleContext context = FrameworkUtil.getBundle(ExtraPropertiesTest.class).getBundleContext();

	private ConfigurationUtil configUtil;

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
		
		Map<String, Object> extras = new HashMap<>();
		extras.put(expectedKey, expectedValue);

		configPids = configUtil.createNettyServer(serverAppName, "localhost", 54323, serverFactoryPids,
				serverHandlerNames, Optional.of(extras));
		configPids.addAll(configUtil.createNettyClient(clientAppName, "localhost", 54323, clientFactoryPids,
				clientHandlerNames, Optional.of(extras), Optional.empty()));
	}

	@After
	public void after() throws Exception {
		configUtil.deleteConfigurationPids(configPids);
	}


	static class ExtraTestServerHandler extends TestServerHandler implements ExtraChannelHandler {
		
	}
	static class ExtraTestClientHandler extends TestClientHandler implements ExtraChannelHandler {
		
	}

	@Test
	public void shouldHaveExtraProperties() throws Exception {

		// Register server handler factory, which will be called
		Hashtable<String, Object> serverHandlerProps = new Hashtable<>();
		CountDownLatch serverLatch = new CountDownLatch(1);
		ExtraPropertiesChannelHandlerFactory serverHandlerFactory = new ExtraPropertiesChannelHandlerFactory(context, ExtraTestServerHandler.class, expectedKey, expectedValue, serverLatch);
		serverHandlerProps.put(Constants.SERVICE_PID, serverFactoryPid);
		ServiceRegistration<ManagedServiceFactory> serverRegistration = context
				.registerService(ManagedServiceFactory.class, serverHandlerFactory, serverHandlerProps);

		// Register client handler factory, which will be called
		Hashtable<String, Object> clientHandlerProps = new Hashtable<>();
		CountDownLatch clientLatch = new CountDownLatch(1);
		ExtraPropertiesChannelHandlerFactory clientHandlerFactory = new ExtraPropertiesChannelHandlerFactory(context, ExtraTestClientHandler.class, expectedKey, expectedValue, clientLatch);
		clientHandlerProps.put(Constants.SERVICE_PID, clientFactoryPid);
		ServiceRegistration<ManagedServiceFactory> clientRegistration = context
				.registerService(ManagedServiceFactory.class, clientHandlerFactory, clientHandlerProps);

		// Verify
		assertTrue(serverLatch.await(3, TimeUnit.SECONDS));
		assertTrue(clientLatch.await(3, TimeUnit.SECONDS));

		// Cleanup
		serverRegistration.unregister();
		clientRegistration.unregister();

	}

	static interface ExtraChannelHandler extends ChannelHandler {
	}
	
	static class ExtraPropertiesChannelHandlerFactory extends TestChannelHandlerFactory {

		private final String expecetdKey;
		private final String expectedValue;
		private final CountDownLatch latch;
		
		public ExtraPropertiesChannelHandlerFactory(BundleContext context,
				Class<? extends ChannelHandler> channelHandlerClass, String expectedKey, String expectedValue, CountDownLatch latch) {
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
				String actualValue = (String)properties.get(expecetdKey);
				if (actualValue != null && actualValue.equals(expectedValue)) {
					latch.countDown();
				}
			}
		}
	}
}
