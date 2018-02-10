package io.blesmol.netty.api;

import io.netty.channel.Channel;
import io.netty.channel.ServerChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

/**
 * <p>
 * Common component property types and configuration PIDs 
 * <p> 
 *
 */
public interface Configuration {

	String NETTY_CLIENT_PID = "io.blesmol.netty.api.NettyClient";
	@interface NettyClient {
		String appName();
		String destinationHost();
		int destinationPort();
		Class<? extends Channel> channel() default NioSocketChannel.class;
		boolean optionAutoRead() default true;
	}
	
	
	String NETTY_SERVER_PID = "io.blesmol.netty.api.NettyServer";
	@interface NettyServer {
		String appName();
		String inetHost() default "localhost";
		int inetPort() default 0;
		Class<? extends ServerChannel> channel() default NioServerSocketChannel.class;	
	}
	
	String OSGI_CHANNEL_HANDLER_PID = "io.blesmol.netty.api.OsgiChannelHandler";
	@interface OsgiChannelHandler {
		String appName();
	}

	// Note: no common PID is used for channel handlers since each
	// should be uniquely configured
	@interface ChannelHandler {
		String appName();
		String handleName();
		boolean first() default false;
		boolean last() default true;
		String before() default "";
		String after() default "";
	}

	String CHANNEL_INITIALIZER_PID = "io.netty.channel.ChannelInitializer";
	@interface ChannelInitializer {
		String appName();		
	}
}
