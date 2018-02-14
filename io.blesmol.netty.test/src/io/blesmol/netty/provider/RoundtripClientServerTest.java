package io.blesmol.netty.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

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
import io.blesmol.netty.api.Property;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

@RunWith(MockitoJUnitRunner.class)
public class RoundtripClientServerTest {

	private final BundleContext context = FrameworkUtil.getBundle(ConfigurationIntegrationTest.class)
			.getBundleContext();

	ConfigurationUtil configUtil;

	NettyServer server;

	static final String appName = RoundtripClientServerTest.class.getName();
	static final String hostname = "localhost";
	static final int port = 5309;

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
        configUtil.createApplication(appName, hostname, port, Arrays.asList(TestServerHandler.class.getName()));
		server = getService(NettyServer.class, 3000, appName);
	}

	@After
	public void after() throws Exception {
        configUtil.deleteApplication(appName);
        server = null;
	}

	public static class TestServerHandler extends ChannelInboundHandlerAdapter {

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			super.channelActive(ctx);
		}
		
		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			ctx.write(msg);
		}

		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) {
			ctx.flush();
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			cause.printStackTrace();
			ctx.close();
		}
	}

	public static class TestClientHandler extends ChannelInboundHandlerAdapter {

		String actual = "";
		String expected = "";

		@Override
		public void channelActive(ChannelHandlerContext ctx) {
			ByteBuf message = Unpooled.buffer(32);
			IntStream.range(0, message.capacity()).forEach(i -> message.writeByte(i));
			expected = message.toString(StandardCharsets.UTF_8);
			ctx.writeAndFlush(message);
		}

		@Override
		public void channelRead(ChannelHandlerContext ctx, Object msg) {
			ByteBuf buf = (ByteBuf) msg;
			actual = buf.toString(StandardCharsets.UTF_8);
		}
		
		@Override
		public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
			ctx.close();
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
			cause.printStackTrace();
			ctx.close();
		}
	}

	public static class TestClient {

		CountDownLatch latch;
		EventLoopGroup group = new NioEventLoopGroup();
		TestClientHandler testHandler = new TestClientHandler();
		ChannelFuture closeFuture;

		public void start() throws Exception {
			Bootstrap b = new Bootstrap();
			b.group(group)
				.channel(NioSocketChannel.class)
				.option(ChannelOption.TCP_NODELAY, true)
				.handler(new ChannelInitializer<SocketChannel>() {
					@Override
					public void initChannel(SocketChannel ch) throws Exception {
						ChannelPipeline p = ch.pipeline();
						p.addLast(testHandler);
					}
				});

			// Start the client.
			closeFuture = b.connect(hostname, port).channel().closeFuture();
			closeFuture.addListener(	new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture future) throws Exception {
					if (future.isSuccess()) {
						latch.countDown();
					}
				}
			});
		}

	}

	@Test
	public void shouldRoundtripMessage() throws Exception {

		// Register server handler
		Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.APP_NAME, appName);
		props.put(Property.ChannelHandler.HANDLE_NAME, TestServerHandler.class.getName());
		props.put(Property.ChannelHandler.FIRST, true);
		TestServerHandler service = new TestServerHandler();
		context.registerService(ChannelHandler.class, service, props);

		// Run client and register latch
		final CountDownLatch latch = new CountDownLatch(1);
		final TestClient client = new TestClient();
		client.latch = latch;
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.execute(new Runnable() {
			@Override
			public void run() {
				try {
					client.start();
				} catch (Exception e) {
					e.printStackTrace();
					fail();
				}
			}
		});
		
		// Verify
		assertTrue(latch.await(2, TimeUnit.SECONDS));
		assertTrue(!client.testHandler.actual.isEmpty());
		assertTrue(!client.testHandler.expected.isEmpty());
		assertEquals(client.testHandler.actual, client.testHandler.expected);
	}

}
