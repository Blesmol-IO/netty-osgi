package io.blesmol.netty.provider;

import org.osgi.service.component.ComponentServiceObjects;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceScope;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.OsgiChannelHandler;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;

@Component(
		service=ChannelInitializer.class,
		configurationPid= Configuration.CHANNEL_INITIALIZER_PID,
		configurationPolicy=ConfigurationPolicy.REQUIRE
	)
public class ChannelInitializerProvider<S extends Channel> extends ChannelInitializer<S> {

	/*
	 * Obtain a component service object to create prototype-scoped channels on-demand
	 * 
	 *  Consider alternatives. It's not obvious on how to integration test
	 *  ComponentServiceObjects. That is, unsure how to obtain one when not
	 *  using DS.
	 */
	@Reference(scope = ReferenceScope.PROTOTYPE_REQUIRED)
	ComponentServiceObjects<OsgiChannelHandler> channelHandlerFactory;

	@Override
	protected void initChannel(S ch) throws Exception {

		// Create a new on-demand service per channel
		final OsgiChannelHandler handler = channelHandlerFactory.getService();
		ch.pipeline().addFirst(handler);
		
		// Unget the service when this channel is closed
		ch.closeFuture().addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				if (channelHandlerFactory != null) {
					channelHandlerFactory.ungetService(handler);
				}
				
			}
		});
	}

}
