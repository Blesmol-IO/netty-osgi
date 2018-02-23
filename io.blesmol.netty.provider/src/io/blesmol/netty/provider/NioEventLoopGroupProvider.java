package io.blesmol.netty.provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import io.blesmol.netty.api.Configuration;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

@Component(configurationPid = Configuration.EVENT_LOOP_GROUP, configurationPolicy = ConfigurationPolicy.REQUIRE,
	service=EventLoopGroup.class
)
public class NioEventLoopGroupProvider extends NioEventLoopGroup {

}
