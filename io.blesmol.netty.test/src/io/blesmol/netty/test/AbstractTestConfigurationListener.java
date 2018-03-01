package io.blesmol.netty.test;

import java.util.concurrent.CountDownLatch;

import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.util.promise.Promise;

public abstract class AbstractTestConfigurationListener implements ConfigurationListener {

	private final CountDownLatch latch;

	public AbstractTestConfigurationListener(CountDownLatch latch) {
		this.latch = latch;
	}

	@Override
	public void configurationEvent(ConfigurationEvent event) {
		isValidEvent(event).onResolve(() -> {
			latch.countDown();
		});
	}

	protected abstract Promise<Boolean> isValidEvent(ConfigurationEvent event);

}
