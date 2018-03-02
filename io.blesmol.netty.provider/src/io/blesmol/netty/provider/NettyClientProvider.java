package io.blesmol.netty.provider;

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

import io.blesmol.netty.api.NettyApi;
import io.blesmol.netty.api.NettyClient;
import io.blesmol.netty.api.ReferenceName;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;

@Component(configurationPid = NettyApi.NettyClient.PID, configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class NettyClientProvider implements NettyClient {

	private final Deferred<ChannelFuture> deferredChannelFuture = new Deferred<>();

	@Reference(name = ReferenceName.NettyClient.BOOTSTRAP)
	Bootstrap bootstrap;

	@Reference(name = ReferenceName.NettyClient.EVENT_LOOP_GROUP)
	EventLoopGroup group;

	@Reference(name = ReferenceName.NettyClient.CHANNEL_INITIALIZER)
	ChannelInitializer<Channel> channelInitializer;

	@Activate
	void activate(NettyApi.NettyClient config, Map<String, ?> properties) {
		System.out.println("Activating netty client with properties " + properties);
		bootstrap.group(group).channel(config.channel()).handler(channelInitializer);

		// https://stackoverflow.com/a/28294255
		// Always disable; channel handler will enable
		bootstrap.option(ChannelOption.AUTO_READ, false);

		System.out.println(String.format("Connecting to server %s:%d", config.inetHost(), config.inetPort()));
		deferredChannelFuture.resolve(bootstrap.connect(config.inetHost(), config.inetPort()));
	}

	@Deactivate
	void deactivate(NettyApi.NettyClient config) {
		// Shutdown group by default
		// Clients being used as a proxy shouldn't shut down their groups
		// TODO: test
		if (config.shutdownGroup()) {
			group.shutdownGracefully();
		}
	}

	@Override
	public Promise<ChannelFuture> promise() {
		return deferredChannelFuture.getPromise();
	}

}
