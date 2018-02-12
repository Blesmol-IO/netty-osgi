package io.blesmol.netty.provider;

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceScope;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.NettyClient;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;

@Component(configurationPid = Configuration.NETTY_CLIENT_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class NettyClientProvider implements NettyClient {

	private ChannelFuture future;

	@Reference(scope = ReferenceScope.PROTOTYPE)
	Bootstrap bootstrap;

	@Reference(scope = ReferenceScope.PROTOTYPE)
	EventLoopGroup group;

	@Reference(scope = ReferenceScope.PROTOTYPE)
	ChannelInitializer<SocketChannel> channelInitializer;

	@Activate
	void activate(Configuration.NettyClient config, Map<String, ?> properties) {
		bootstrap.group(group).channel(config.channel()).handler(channelInitializer);

		if (!config.optionAutoRead()) {
			// https://stackoverflow.com/a/28294255
			bootstrap.option(ChannelOption.AUTO_READ, false);
		}

		future = bootstrap.connect(config.destinationHost(), config.destinationPort());
	}

	@Deactivate
	void deactivate(Configuration.NettyClient config) {
		group.shutdownGracefully();
	}

	@Override
	public ChannelFuture future() {
		return future;
	}

}
