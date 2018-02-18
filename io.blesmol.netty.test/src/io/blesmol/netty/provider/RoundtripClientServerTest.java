package io.blesmol.netty.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
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
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.cm.ManagedServiceFactory;

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
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

@RunWith(MockitoJUnitRunner.class)
public class RoundtripClientServerTest {

	final BundleContext context = FrameworkUtil.getBundle(ConfigurationIntegrationTest.class)
			.getBundleContext();
	

	private ConfigurationUtil configUtil;

	private NettyServer server;
	
	String configPid;

	static final String appName = RoundtripClientServerTest.class.getName();
	static final String factoryPid = RoundtripClientServerTest.class.getName();
	static final List<String> factoryPids = Arrays.asList(factoryPid);
	static final List<String> handlerNames = Arrays.asList("testServerHandler1");

	@Before
	public void before() throws Exception {
		configUtil = TestUtils.getService(context, ConfigurationUtil.class, 700);
		configPid = configUtil.createNettyServerConfig(appName, "localhost", /*ephemeral*/0, factoryPids, handlerNames);
		String filter = String.format("(&(appName=%s))", appName);
		server = TestUtils.getService(context, NettyServer.class, 3000, filter);
	}

	@After
	public void after() throws Exception {
		configUtil.deleteNettyServerConfig(configPid);
        server = null;
	}

	class TestServerHandlerFactory implements ManagedServiceFactory {

		ServiceRegistration<ChannelHandler> sr;

		@Override
		public String getName() {
			return TestServerHandlerFactory.class.getName();
		}

		@Override
		public void updated(String pid, Dictionary<String, ?> properties) throws ConfigurationException {
			System.out.println("test factory creating service via update with properties " + properties);
			sr = context.registerService(ChannelHandler.class, new TestServerHandler(), properties);	
		}

		@Override
		public void deleted(String pid) {
			sr.unregister();
			
		}
		
	}
	
	static class TestServerHandler extends ChannelInboundHandlerAdapter {

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

	static class TestClientHandler extends ChannelInboundHandlerAdapter {

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

	static class TestClient {

		final String hostname;
		final int port;
		TestClient(String hostname, int port) {
			this.hostname = hostname;
			this.port = port;
		}
		CountDownLatch latch;
		EventLoopGroup group = new NioEventLoopGroup();
		TestClientHandler testHandler = new TestClientHandler();
		ChannelFuture closeFuture;

		public void start() throws Exception {
			final Bootstrap b = new Bootstrap();
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
					} else {
						throw new RuntimeException("Connection unsuccessful");
					}
				}
			});
		}

	}

	@Test
	public void shouldRoundtripMessage() throws Exception {

		// Register server handler factory, which will be called
		Hashtable<String, Object> props = new Hashtable<>();
		TestServerHandlerFactory factory = new TestServerHandlerFactory();
		props.put(Constants.SERVICE_PID, factoryPid);
		context.registerService(ManagedServiceFactory.class.getName(), factory, props);

		// Run client and register latch
		final CountDownLatch latch = new CountDownLatch(1);
		final ChannelFuture future = server.promise().getValue();
		final ServerSocketChannel serverChannel = (ServerSocketChannel) future.channel();
		// Dunno why, but printing out the channel prevents an NPE when accessing the port...
		System.out.println(serverChannel);
		// get the ephemeral port
		int port = serverChannel.localAddress().getPort();
		final TestClient client = new TestClient("localhost", port);
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
