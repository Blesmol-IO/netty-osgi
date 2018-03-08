package io.blesmol.netty.provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import io.blesmol.netty.api.NettyApi;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.util.concurrent.EventExecutorGroup;

@Component(configurationPid = NettyApi.EventExecutorGroup.PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = EventExecutorGroup.class)
public class EventExecutorGroupProvider extends DefaultEventLoopGroup {

}
