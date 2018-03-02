package io.blesmol.netty.provider;

import java.util.Map;
import java.util.concurrent.Executor;

import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import io.blesmol.netty.api.NettyApi;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;

@Component(configurationPid = NettyApi.EventLoopGroup.PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = EventLoopGroup.class)
public class EventLoopGroupProvider extends NioEventLoopGroup {

	private String pid;
	private String appName;
	private String groupName;

	@Activate
	void activate(NettyApi.EventLoopGroup config, Map<String, Object> properties) {
		this.appName = config.appName();
		this.groupName = config.groupName();
		this.pid = (String) properties.get(Constants.SERVICE_PID);
		System.out.println("Activated " + this);
	}

	@Override
	public String toString() {
		return pid + ":" + appName + ":" + groupName;
	}

	@Override
	protected EventLoop newChild(Executor executor, Object... args) throws Exception {
		final EventLoop eventLoop = super.newChild(executor, args);
		System.out.println(String.format("Creating new event loop %s", eventLoop));
		return eventLoop;
	}

}
