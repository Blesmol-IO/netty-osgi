package io.blesmol.netty.provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import io.blesmol.netty.api.Configuration;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.util.concurrent.EventExecutorGroup;

@Component(configurationPid = Configuration.EVENT_EXECUTOR_GROUP, configurationPolicy = ConfigurationPolicy.REQUIRE, service = EventExecutorGroup.class)
public class EventExecutorGroupProvider extends NioEventLoopGroup {

}
