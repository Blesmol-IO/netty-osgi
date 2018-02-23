package io.blesmol.netty.provider;

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.util.promise.Deferred;
import org.osgi.util.promise.Promise;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.NettyClient;
import io.blesmol.netty.api.ReferenceName;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;

@Component(configurationPid = Configuration.NETTY_CLIENT_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class NettyClientProvider implements NettyClient {

	private final Deferred<ChannelFuture> deferredChannelFuture = new Deferred<>();

	@Reference
	Bootstrap bootstrap;

	@Reference(name = ReferenceName.NettyClient.EVENT_LOOP_GROUP)
	EventLoopGroup group;

	@Reference
	ChannelInitializer<Channel> channelInitializer;

	@Activate
	void activate(Configuration.NettyClient config, Map<String, ?> properties) {
		System.out.println("Activating netty client with properties " + properties);
		bootstrap.group(group).channel(config.channel()).handler(channelInitializer);

			// https://stackoverflow.com/a/28294255
			// Always disable; channel handler will enable
			bootstrap.option(ChannelOption.AUTO_READ, false);
		if (!config.optionAutoRead()) {
			// https://stackoverflow.com/a/28294255
			bootstrap.option(ChannelOption.AUTO_READ, false);
		}

		System.out.println(String.format("Connecting to server %s:%d", config.inetHost(), config.inetPort()));
		deferredChannelFuture.resolve(bootstrap.connect(config.inetHost(), config.inetPort()));
	}

	@Deactivate
	void deactivate(Configuration.NettyClient config) {
		group.shutdownGracefully();
	}

	@Override
	public Promise<ChannelFuture> promise() {
		return deferredChannelFuture.getPromise();
	}

}
