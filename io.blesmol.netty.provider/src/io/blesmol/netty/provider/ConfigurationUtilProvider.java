package io.blesmol.netty.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

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

	private String createConfiguration(String factoryPid, Dictionary<String, Object> properties) throws Exception {
		Configuration configuration = admin.createFactoryConfiguration(factoryPid, "?");
		configuration.update(properties);
		configurations.add(configuration);
		return configuration.getPid();
	}

	@Override
	public String[] createNettyServer(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception {

		List<String> results = new ArrayList<>();
		results.add(createEventLoopGroup(appName, ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP));
		results.add(createEventLoopGroup(appName, ReferenceName.NettyServer.WORKER_EVENT_LOOP_GROUP));
		results.add(createNettyServerConfig(appName, hostname, port, factoryPids, handlerNames, extraProperties));

		return results.toArray(new String[0]);

	}

	@Override
	public String createEventLoopGroup(String appName, String groupName) throws Exception {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.EventLoopGroup.APP_NAME, appName);
		props.put(Property.EventLoopGroup.GROUP_NAME, groupName);
		return createConfiguration(io.blesmol.netty.api.Configuration.EVENT_LOOP_GROUP, props);
	}

	@Override
	public String createNettyServerConfig(String appName, String hostname, Integer port, List<String> handlers,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception {

		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.NettyServer.APP_NAME, appName);

		final String[] propHandlers = handlers.toArray(EMPTY_ARRAY);
		// FIXME: ldap injection
		String channelInitializerTarget = String.format("(&(%s=%s)(%s=%s)(%s=%d)(%s=%s))",
				Property.ChannelInitializer.APP_NAME, appName, Property.ChannelInitializer.INET_HOST, hostname,
				Property.ChannelInitializer.INET_PORT, port, Property.ChannelInitializer.FACTORY_PIDS, propHandlers);
		props.put(ReferenceName.NettyServer.CHANNEL_INITIALIZER_TARGET, channelInitializerTarget);

		// Target event groups at the application level currently
		String bossGroupTarget = String.format("(&(%s=%s)(%s=%s))", Property.EventLoopGroup.APP_NAME, appName,
				Property.EventLoopGroup.GROUP_NAME, ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP); 				
		props.put(ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP_TARGET, bossGroupTarget);
		String workerGroupTarget = String.format("(&(%s=%s)(%s=%s))", Property.EventLoopGroup.APP_NAME, appName,
				Property.EventLoopGroup.GROUP_NAME, ReferenceName.NettyServer.WORKER_EVENT_LOOP_GROUP);
		props.put(ReferenceName.NettyServer.WORKER_EVENT_LOOP_GROUP_TARGET, workerGroupTarget);

		props.put(Property.NettyServer.INET_HOST, hostname);
		props.put(Property.NettyServer.INET_PORT, port);
		props.put(Property.NettyServer.FACTORY_PIDS, propHandlers);
		props.put(Property.NettyServer.HANDLER_NAMES, handlerNames.toArray(EMPTY_ARRAY));

		if (extraProperties.isPresent()) {
			extraProperties.get().entrySet().forEach(es -> {
				props.put(Property.EXTRA_PREFIX + es.getKey(), es.getValue());
			});
			props.put(Property.EXTRA_PROPERTIES, true);
		}

		return createConfiguration(io.blesmol.netty.api.Configuration.NETTY_SERVER_PID, props);

	}

	@Override
	public void deleteConfigurationPid(String... configurationPids) throws Exception {

		for (String pid : configurationPids) {
			final String filter = String.format("(%s=%s)", Constants.SERVICE_PID, pid);
	
			Configuration[] configs = admin.listConfigurations(filter);
			if (configs == null) {
				// TODO debug/trace log
				System.err.println("No configurations to delete for filter " + filter);
				continue;
			}
			for (Configuration config : configs) {
				configurations.remove(config);
				config.delete();
			}
		}
	}

	@Override
	public Dictionary<String, Object> toChannelHandlerProps(String handlerName, String channelId,
			Optional<Map<String, Object>> extraProperties) {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.ChannelHandler.HANDLER_NAME, handlerName);
		props.put(Property.ChannelHandler.CHANNEL_ID, channelId);

		if (extraProperties.isPresent()) {
			Map<String, Object> extraProps = extraProperties.get();
			// remove flag
			extraProps.remove(Property.EXTRA_PROPERTIES);
			// Undo prefix addition
			props.putAll(extraProps.entrySet().stream().collect(Collectors
					.toMap(es -> es.getKey().substring(Property.EXTRA_PREFIX.length()), es -> es.getValue())));
		}
		return props;
	}

	@Override
	public Dictionary<String, Object> toDynamicChannelHandlerProperties(String channelId, String appName,
			String hostname, int port, String[] factoryPids, String[] handlerNames,
			Optional<Map<String, Object>> extraProperties) {

		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.OsgiChannelHandler.CHANNEL_ID, channelId);
		props.put(Property.OsgiChannelHandler.APP_NAME, appName);
		props.put(Property.OsgiChannelHandler.INET_HOST, hostname);
		props.put(Property.OsgiChannelHandler.INET_PORT, port);
		props.put(Property.OsgiChannelHandler.FACTORY_PIDS, factoryPids);
		props.put(Property.OsgiChannelHandler.HANDLER_NAMES, handlerNames);

		// store extra properties as handler properties
		extraProperties.ifPresent(ep -> props.putAll(ep));

		// bind this dynamic handler
		props.put(ReferenceName.OsgiChannelHandler.CHANNEL_HANDLER_TARGET,
				String.format("(%s=%s)", Property.ChannelHandler.CHANNEL_ID, channelId));

		return props;
	}

	@Override
	public Dictionary<String, Object> toChannelInitializerProperties(String appName, String hostname, int port,
			String[] factoryPids, String[] handlerNames, Optional<Map<String, Object>> extraProperties) {

		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.ChannelInitializer.APP_NAME, appName);
		props.put(Property.ChannelInitializer.INET_HOST, hostname);
		props.put(Property.ChannelInitializer.INET_PORT, port);
		props.put(Property.ChannelInitializer.FACTORY_PIDS, factoryPids);
		props.put(Property.ChannelInitializer.HANDLER_NAMES, handlerNames);

		// store extra properties as handler properties
		extraProperties.ifPresent(ep -> props.putAll(ep));

		props.put(ReferenceName.ChannelInitializer.CHANNEL_HANDLER_FACTORY_TARGET,
				String.format("(&(%s=%s)(%s=%s)(%s=%d))", Property.ChannelInitializer.APP_NAME, appName,
						Property.ChannelInitializer.INET_HOST, hostname, Property.ChannelInitializer.INET_PORT, port));
		return props;
	}

	@Override
	public Optional<Map<String, Object>> toOptionalExtraProperties(Map<String, Object> properties) {
		Optional<Map<String, Object>> result = Optional.empty();
		if (properties.containsKey(Property.EXTRA_PROPERTIES)) {
			final Map<String, Object> extraMap = properties.entrySet().stream()
					.filter(es -> es.getKey().startsWith(Property.EXTRA_PREFIX))
					.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
			extraMap.put(Property.EXTRA_PROPERTIES, true);
			result = Optional.of(extraMap);
		}
		return result;
	}

}
