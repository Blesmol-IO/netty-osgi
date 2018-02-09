package io.blesmol.netty.provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

@Component(
	scope=ServiceScope.PROTOTYPE,
	service=EventLoopGroup.class
)
public class NioEventLoopGroupProvider extends NioEventLoopGroup {

}
