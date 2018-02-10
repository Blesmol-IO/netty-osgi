package io.blesmol.netty.provider;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;

import io.netty.bootstrap.Bootstrap;

@Component(
	scope=ServiceScope.PROTOTYPE,
	service=Bootstrap.class
)
public class BootstrapProvider extends Bootstrap {

}
