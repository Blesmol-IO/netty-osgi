package io.blesmol.netty.example;

import java.util.ArrayList;
import java.util.Optional;

import org.osgi.service.component.annotations.*;

import io.blesmol.netty.api.ConfigurationUtil;

@Component(immediate=true)
public class Application {

	@Reference
	ConfigurationUtil configUtil;

	String configPid;
	
	@Activate
	void activate() throws Exception {
		configPid = configUtil.createNettyServerConfig(Application.class.getName(), "localhost", 8484, new ArrayList<>(), new ArrayList<>(), Optional.empty());
	}

	@Deactivate
	void deactivate() throws Exception {
		configUtil.deleteNettyServerConfig(configPid);
	}

}
