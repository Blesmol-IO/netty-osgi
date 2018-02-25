package io.blesmol.netty.provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import io.blesmol.netty.api.Configuration;
import io.netty.bootstrap.Bootstrap;

@Component(configurationPid = Configuration.BOOTSTRAP_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = Bootstrap.class)
public class BootstrapProvider extends Bootstrap {

}
