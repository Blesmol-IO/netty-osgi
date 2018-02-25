package io.blesmol.netty.api;

import io.netty.util.concurrent.EventExecutorGroup;

public interface EventExecutorGroupProvider {

	EventExecutorGroup getEventExecutorGroup();
}
