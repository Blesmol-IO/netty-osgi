package io.blesmol.netty.provider;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.NettyServer;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

@RunWith(MockitoJUnitRunner.class)
public class ConfigurationIntegrationTest {

	private final BundleContext context = FrameworkUtil.getBundle(ConfigurationIntegrationTest.class).getBundleContext();

	ConfigurationUtil configUtil;

	// TODO: don't leaks service trackers
	<T> T getService(Class<T> clazz, long timeout) throws Exception {
		return getService(clazz, timeout, "");
	}

	<T> T getService(Class<T> clazz, long timeout, String appName) throws Exception {
		ServiceTracker<T,T> st;
		if (appName != null && !"".equals(appName)) {
			Filter filter = context.createFilter(
					String.format("(&(%s=%s)(appName=%s))", Constants.OBJECTCLASS, clazz.getName(), appName)
			);
			st = new ServiceTracker<>(context, filter, null);
			
		} else {
			st = new ServiceTracker<>(context, clazz, null);
		}
		st.open();
		return st.waitForService(timeout);
	}

	@Before
	public void before() throws Exception {
		configUtil = getService(ConfigurationUtil.class, 700);
	}

	@After
	public void after() {
		configUtil = null;
	}

	// Note: test waits arbitrary times for services to be registered
	@Test
	public void shouldCreateConfigAndGetService() throws Exception {
		final String appName = "shouldCreateConfigAndGetService";

		// Create the server config, channel initializer, and dynamic handler
        configUtil.createNettyServerConfig(appName, "localhost", 5308);
        configUtil.createChannelInitializerConfig(appName);
        configUtil.createOsgiChannelHandlerConfig(appName);

        // Verify service creation and pipeline being established
        NettyServer server = getService(NettyServer.class, 3000, appName);
        assertNotNull(server);
        assertNotNull(server.bindFuture());

        // Setup latch
        final CountDownLatch latch = new CountDownLatch(1);
        server.bindFuture().channel().closeFuture().addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				latch.countDown();
			}
        });

        // Delete the config and wait so the service manager factory can
        // unregister the service
        configUtil.deleteOsgiChannelHandlerConfig(appName);
        configUtil.deleteChannelInitializerConfig(appName);
        configUtil.deleteNettyServerConfig(appName);
        assertTrue(latch.await(2, TimeUnit.SECONDS));
	}

	
}