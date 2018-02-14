package io.blesmol.netty.provider;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.api.ReferenceName;

@Component(immediate = true)
public class ConfigurationUtilProvider implements ConfigurationUtil {

	@Reference
	ConfigurationAdmin admin;

	@Activate
	void activate() {
	}

	@Deactivate
	void deactivate() {
		configurations.forEach(c -> {
			try {
				c.delete();
			}
			// swallow
			catch (IOException e) {
			}
		});
	}

	List<Configuration> configurations = new CopyOnWriteArrayList<>();

	@Override
	public void createApplication(String appName, String hostname, Integer port, List<String> handlers) throws Exception {
        createNettyServerConfig(appName, hostname, port);
        createChannelInitializerConfig(appName);
        createOsgiChannelHandlerConfig(appName, handlers);
	}

	@Override
	public void createNettyServerConfig(String appName, String hostname, Integer port) throws Exception {

		Configuration config = admin.createFactoryConfiguration(io.blesmol.netty.api.Configuration.NETTY_SERVER_PID,
				"?");
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.APP_NAME, appName);
		props.put(Property.NettyServer.INET_HOST, hostname);
		props.put(Property.NettyServer.INET_PORT, port);
		config.update(props);
		configurations.add(config);
	}

	@Override
	public void createOsgiChannelHandlerConfig(String appName, List<String> handlers) throws Exception {

		Configuration config = admin
				.createFactoryConfiguration(io.blesmol.netty.api.Configuration.OSGI_CHANNEL_HANDLER_PID, "?");
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.OsgiChannelHandler.APP_NAME, appName);
		// bind the app name to the target reference
		props.put(ReferenceName.OsgiChannelHandler.CHANNEL_HANDLER + ".target",
				String.format("(%s=%s)", Property.APP_NAME, appName));
		// Add empty handler list
		props.put(Property.OsgiChannelHandler.HANDLERS, handlers.toArray(new String[0]));
		config.update(props);
		configurations.add(config);
	}

	@Override
	public void createChannelInitializerConfig(String appName) throws Exception {

		Configuration config = admin
				.createFactoryConfiguration(io.blesmol.netty.api.Configuration.CHANNEL_INITIALIZER_PID, "?");
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.APP_NAME, appName);
		config.update(props);
		configurations.add(config);

	}

	@Override
	public void deleteApplication(String appName) throws Exception {
        deleteOsgiChannelHandlerConfig(appName);
        deleteChannelInitializerConfig(appName);
        deleteNettyServerConfig(appName);		
	}

	@Override
	public void deleteNettyServerConfig(String appName) throws Exception {
		Configuration[] configs = admin.listConfigurations(String.format("(&(service.factoryPid=%s)(%s=%s))",
				io.blesmol.netty.api.Configuration.NETTY_SERVER_PID, Property.APP_NAME, appName));
		for (Configuration config : configs) {
			configurations.remove(config);
			config.delete();
		}
	}

	@Override
	public void deleteOsgiChannelHandlerConfig(String appName) throws Exception {
		Configuration[] configs = admin.listConfigurations(String.format("(&(service.factoryPid=%s)(%s=%s))",
				io.blesmol.netty.api.Configuration.OSGI_CHANNEL_HANDLER_PID, Property.APP_NAME, appName));
		for (Configuration config : configs) {
			configurations.remove(config);
			config.delete();
		}

	}

	@Override
	public void deleteChannelInitializerConfig(String appName) throws Exception {
		Configuration[] configs = admin.listConfigurations(String.format("(&(service.factoryPid=%s)(%s=%s))",
				io.blesmol.netty.api.Configuration.CHANNEL_INITIALIZER_PID, Property.APP_NAME, appName));
		for (Configuration config : configs) {
			configurations.remove(config);
			config.delete();
		}

	}

}
