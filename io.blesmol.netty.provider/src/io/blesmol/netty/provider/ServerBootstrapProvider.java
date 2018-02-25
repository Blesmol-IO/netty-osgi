package io.blesmol.netty.provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import io.blesmol.netty.api.Configuration;
import io.netty.bootstrap.ServerBootstrap;

@Component(configurationPid = Configuration.SERVER_BOOTSTRAP_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, service = ServerBootstrap.class)
public class ServerBootstrapProvider extends ServerBootstrap {

}
