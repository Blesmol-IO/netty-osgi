package io.blesmol.netty.provider;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;

import io.blesmol.netty.api.NettyApi;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;

@Component(configurationPid = NettyApi.Bootstrap.PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = Bootstrap.class)
public class BootstrapProvider extends Bootstrap {

	@Reference(name = NettyApi.Bootstrap.Reference.EVENT_LOOP_GROUP)
	void setEventLoopGroup(EventLoopGroup group) {
		this.group(group);
	}

	void unsetEventLoopGroup(EventLoopGroup group) {
	}
	
	@Reference(name = NettyApi.Bootstrap.Reference.CHANNEL_INITIALIZER)
	void setChannelInitializer(ChannelInitializer<Channel> channelInitializer) {
		this.handler(channelInitializer);
	}
	void unsetChannelInitializer(ChannelInitializer<Channel> channelInitializer) {
	}

	@Activate
	void activate(NettyApi.Bootstrap config) {
		// TODO: move channel to channel factory and consume a provider factory
		this.option(ChannelOption.AUTO_READ, false).channel(NioSocketChannel.class);

	}
}
