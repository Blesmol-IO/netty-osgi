package io.blesmol.netty.api;

public interface NettyApi {

	String APP_NAME = "appName";

	@interface EventLoopGroup {
		String PID = "io.netty.channel.EventLoopGroup";
		String NAME = "nettyEventLoopGroup";

		String APP_NAME = NettyApi.APP_NAME;

		String appName();

		String GROUP_NAME = "groupName";

		String groupName();

		String INET_HOST = "inetHost";

		String inetHost();

		String INET_PORT = "inetPort";

		int inetPort();
	}
}
