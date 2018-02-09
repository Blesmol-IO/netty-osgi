package io.blesmol.netty.provider;

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceScope;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.NettyServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;

@Component(
	configurationPid = Configuration.NETTY_SERVER_PID,
	configurationPolicy = ConfigurationPolicy.REQUIRE
)
public class NettyServerProvider implements NettyServer {

	private ChannelFuture future;

	@Reference(scope=ReferenceScope.PROTOTYPE)
	ServerBootstrap server;

	@Reference(scope=ReferenceScope.PROTOTYPE)
	EventLoopGroup bossGroup;

	@Reference(scope=ReferenceScope.PROTOTYPE)
	EventLoopGroup workerGroup;

	@Reference
	ChannelInitializer<SocketChannel> channelInitializer;
	
	@Activate
	void activate(Configuration.NettyServer config, Map<String, ?> properties) {
		server.group(bossGroup, workerGroup)
			.channel(config.channel())
			.option(ChannelOption.SO_BACKLOG, 128)
			.childHandler(channelInitializer)
			.childOption(ChannelOption.SO_KEEPALIVE, true);
		future = server.bind(config.inetHost(), config.inetPort());
	}

	@Deactivate
	void deactivate(Configuration.NettyServer config, Map<String, ?> properties) {
		// Gracefully clean-up
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }

	@Override
	public ChannelFuture bindFuture() {
		return future;
	}

	
}
