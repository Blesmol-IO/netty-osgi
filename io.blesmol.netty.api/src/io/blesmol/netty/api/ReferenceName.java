package io.blesmol.netty.api;

/**
 * Highly recommended reference names for providers
 * 
 * @see org.osgi.service.component.annotations.Reference
 */
public interface ReferenceName {

	String DOT_TARGET = ".target";

	interface OsgiChannelHandler {
		String CHANNEL_HANDLER = "channelHandler";
		String CHANNEL_HANDLER_TARGET = CHANNEL_HANDLER + DOT_TARGET;
	}

	interface ChannelInitializer {
		String CHANNEL_HANDLER_FACTORY = "channelHandlerFactory";
		String CHANNEL_HANDLER_FACTORY_TARGET = CHANNEL_HANDLER_FACTORY + DOT_TARGET;
	}

	interface NettyServer {
		String CHANNEL_INITIALIZER = "channelInitializer";
		String CHANNEL_INITIALIZER_TARGET = CHANNEL_INITIALIZER + DOT_TARGET;
		String BOSS_EVENT_LOOP_GROUP = "bossGroup";
		String BOSS_EVENT_LOOP_GROUP_TARGET = BOSS_EVENT_LOOP_GROUP + DOT_TARGET;
		String WORKER_EVENT_LOOP_GROUP = "workerGroup";
		String WORKER_EVENT_LOOP_GROUP_TARGET = WORKER_EVENT_LOOP_GROUP + DOT_TARGET;
	}
}
