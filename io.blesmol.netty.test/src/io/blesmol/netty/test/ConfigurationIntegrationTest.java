package io.blesmol.netty.test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.NettyServer;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationIntegrationTest {

	private final BundleContext context = FrameworkUtil.getBundle(ConfigurationIntegrationTest.class)
			.getBundleContext();

	ConfigurationUtil configUtil;

	@Before
	public void before() throws Exception {
		configUtil = TestUtils.getService(context, ConfigurationUtil.class, 700);
	}

	@After
	public void after() {
		configUtil = null;
	}

	// Note: test waits arbitrary times for services to be registered
	@Test
	public void shouldCreateConfigAndGetService() throws Exception {
		final String appName = "shouldCreateConfigAndGetService";
		final String hostname = "localhost";
		final int port = 0; // ephemeral
		final List<String> factoryPids = new ArrayList<>();
		final List<String> handlerNames = new ArrayList<>();

		// Create the server config, channel initializer, and dynamic handler
		List<String> pids = configUtil.createNettyServer(appName, hostname, port, factoryPids, handlerNames, Optional.empty());

		// Verify service creation and pipeline being established
//		ServiceReference<NettyServer> reference = TestUtils.getTracker(context, NettyServer.class).getServiceReference();
//		NettyServer server =  context.getService(reference); // TestUtils.getService(context, NettyServer.class, 3000, appName);
		NettyServer server = TestUtils.getService(context, NettyServer.class, 3000);
		assertNotNull(server);
		assertNotNull(server.promise());

		// Setup latch
		final CountDownLatch latch = new CountDownLatch(1);
		server.promise().getValue().channel().closeFuture().addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				latch.countDown();
			}
		});

		// Delete the config and wait so the service manager factory can
		// unregister the service
		configUtil.deleteConfigurationPids(pids);
//		context.ungetService(reference);
		assertTrue(latch.await(2, TimeUnit.SECONDS));
	}

}