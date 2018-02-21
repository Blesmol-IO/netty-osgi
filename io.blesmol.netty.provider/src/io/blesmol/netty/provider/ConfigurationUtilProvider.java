package io.blesmol.netty.provider;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.framework.Constants;
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

	private final AtomicBoolean deactivated = new AtomicBoolean(false);

	@Reference
	ConfigurationAdmin admin;

	@Activate
	void activate() {
	}

	@Deactivate
	void deactivate() {
		if (deactivated.getAndSet(true) == true) {
			return;
		}

		// TODO trace log
		System.out.println("Deactivating configuration utility provider");
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
	public String createNettyServerConfig(String appName, String hostname, Integer port, List<String> handlers,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception {

		Configuration config = admin.createFactoryConfiguration(io.blesmol.netty.api.Configuration.NETTY_SERVER_PID,
				"?");
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.NettyServer.APP_NAME, appName);

		final String[] propHandlers = handlers.toArray(new String[0]);
		// FIXME: ldap injection
		String channelInitializerTarget = String.format("(&(%s=%s)(%s=%s)(%s=%d)(%s=%s))",
				Property.ChannelInitializer.APP_NAME, appName, Property.ChannelInitializer.INET_HOST, hostname,
				Property.ChannelInitializer.INET_PORT, port, Property.ChannelInitializer.FACTORY_PIDS, propHandlers);

		props.put(ReferenceName.NettyServer.CHANNEL_INITIALIZER, channelInitializerTarget);
		props.put(Property.NettyServer.INET_HOST, hostname);
		props.put(Property.NettyServer.INET_PORT, port);
		props.put(Property.NettyServer.FACTORY_PIDS, propHandlers);
		props.put(Property.NettyServer.HANDLER_NAMES, handlerNames.toArray(new String[0]));

		if (extraProperties.isPresent()) {
			extraProperties.get().entrySet().forEach(es -> {
				props.put(Property.EXTRA_PREFIX + es.getKey(), es.getValue());
			});
			props.put(Property.EXTRA_PROPERTIES, true);
		}

		config.update(props);
		configurations.add(config);

		return config.getPid();
	}

	@Override
	public void deleteNettyServerConfig(String pid) throws Exception {

		final String filter = String.format("(%s=%s)", Constants.SERVICE_PID, pid);

		Configuration[] configs = admin.listConfigurations(filter);
		if (configs == null) {
			// TODO debug/trace log
			System.err.println("No configurations to delete for filter " + filter);
			return;
		}
		for (Configuration config : configs) {
			configurations.remove(config);
			config.delete();
		}
	}

}
