package io.blesmol.netty.example;

import org.osgi.service.component.annotations.*;

import io.blesmol.netty.api.ConfigurationUtil;

@Component(immediate=true)
public class Application {

	@Reference
	ConfigurationUtil configUtil;

	@Activate
	void activate() throws Exception {
		configUtil.createApplication(Application.class.getName(), "localhost", 8484);
	}

	@Deactivate
	void deactivate() throws Exception {
		configUtil.deleteApplication(Application.class.getName());
	}

}
