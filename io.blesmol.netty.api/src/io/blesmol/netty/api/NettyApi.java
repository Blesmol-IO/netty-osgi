package io.blesmol.netty.api;

public interface NettyApi {

	String APP_NAME = "appName";

	@interface EventLoopGroup {
		String PID = "io.netty.channel.EventLoopGroup";
		String NAME = "nettyEventLoopGroup";

		String APP_NAME = NettyApi.APP_NAME;

		String appName();

		String INET_HOST = "inetHost";

		String inetHost();

		String INET_PORT = "inetPort";

		int inetPort();
		
		String GROUP_NAME = "groupName";

		String groupName();
	}
	

	@interface EventExecutorGroup {
		
		String PID = "io.netty.util.concurrent.EventExecutorGroup";
		String NAME = "nettyEventExecutorGroup";

		String APP_NAME = NettyApi.APP_NAME;

		String appName();

		String INET_HOST = "inetHost";

		String inetHost();

		String INET_PORT = "inetPort";

		int inetPort();
		
		String GROUP_NAME = "groupName";

		String groupName();
	}

}
