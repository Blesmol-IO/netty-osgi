package io.blesmol.netty.provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

import io.netty.bootstrap.ServerBootstrap;

@Component(
		scope=ServiceScope.PROTOTYPE,
		service=ServerBootstrap.class
)
public class ServerBootstrapProvider extends ServerBootstrap {

}
