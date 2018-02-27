package io.blesmol.netty.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
	public List<String> createNettyServer(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception {

		final List<String> results = new ArrayList<>();
		results.add(createServerBootstrapProvider(appName, hostname, port));
		results.add(createEventLoopGroup(appName, ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP));
		results.add(createEventLoopGroup(appName, ReferenceName.NettyServer.WORKER_EVENT_LOOP_GROUP));
		results.addAll(createChannelInitializer(appName, hostname, port, factoryPids, handlerNames, extraProperties));
		results.add(createNettyServerConfig(appName, hostname, port, factoryPids, handlerNames, extraProperties));
		return results;

	}

	@Override
	public List<String> createNettyClient(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, Optional<String> serverAppName)
			throws Exception {

		final List<String> results = new ArrayList<>();
		results.add(createBootstrapProvider(appName, hostname, port, serverAppName));
		results.add(createEventLoopGroup(appName, ReferenceName.NettyClient.EVENT_LOOP_GROUP));
		results.addAll(createChannelInitializer(appName, hostname, port, factoryPids, handlerNames, extraProperties));
		results.add(createNettyClientConfig(appName, hostname, port, factoryPids, handlerNames, extraProperties,
				serverAppName));
		return results;
	}

	@Override
	public String createEventLoopGroup(String appName, String groupName) throws Exception {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.EventLoopGroup.APP_NAME, appName);
		props.put(Property.EventLoopGroup.GROUP_NAME, groupName);
		return createConfiguration(io.blesmol.netty.api.Configuration.EVENT_LOOP_GROUP, props);
	}

	@Override
	public String createEventExecutorGroup(String appName, String groupName) throws Exception {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.EventExecutorGroup.APP_NAME, appName);
		props.put(Property.EventExecutorGroup.GROUP_NAME, groupName);
		return createConfiguration(io.blesmol.netty.api.Configuration.EVENT_EXECUTOR_GROUP, props);
	}

	@Override
	public List<String> createChannelInitializer(String appName, String hostname, int port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception {

		final List<String> results = new ArrayList<>();
		results.add(createEventExecutorGroup(appName, ReferenceName.ChannelInitializer.EVENT_EXECUTOR_GROUP));
		results.add(
				createChannelInitializerConfig(appName, hostname, port, factoryPids, handlerNames, extraProperties));
		return results;
	}

	@Override
	public String createChannelInitializerConfig(String appName, String hostname, int port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception {

		final Dictionary<String, Object> props = toChannelInitializerProperties(appName, hostname, port, factoryPids,
				handlerNames, extraProperties);
		return createConfiguration(io.blesmol.netty.api.Configuration.CHANNEL_INITIALIZER_PID, props);
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
		addExtraProperties(props, extraProperties);

		// Target dynamic channel handler
		final String handlerTarget = String.format("(&(%s=%s)(%s=%s)(%s=%d))", Property.DynamicChannelHandler.APP_NAME,
				appName, Property.DynamicChannelHandler.INET_HOST, hostname, Property.DynamicChannelHandler.INET_PORT, port);
		props.put(ReferenceName.ChannelInitializer.CHANNEL_HANDLERS_TARGET, handlerTarget);

		// Target event executor group
		final String eventTarget = String.format("(&(%s=%s)(%s=%s))", Property.EventExecutorGroup.APP_NAME, appName,
				Property.EventExecutorGroup.GROUP_NAME, ReferenceName.ChannelInitializer.EVENT_EXECUTOR_GROUP);
		props.put(ReferenceName.ChannelInitializer.EVENT_EXECUTOR_GROUP_TARGET, eventTarget);

		return props;
	}

	@Override
	public String createDynamicChannelHandlerConfig(String channelId, String appName, String hostname, int port,
			List<String> factoryPids, List<String> handlerNames, Optional<Map<String, Object>> extraProperties)
			throws Exception {

		final Dictionary<String, Object> props = toDynamicChannelHandlerProperties(channelId, appName, hostname, port,
				factoryPids, handlerNames, extraProperties);
		return createConfiguration(io.blesmol.netty.api.Configuration.DYNAMIC_CHANNEL_HANDLER_PID, props);
	}

	@Override
	public Dictionary<String, Object> toDynamicChannelHandlerProperties(String channelId, String appName,
			String hostname, int port, String[] factoryPids, String[] handlerNames,
			Optional<Map<String, Object>> extraProperties) {

		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.DynamicChannelHandler.CHANNEL_ID, channelId);
		props.put(Property.DynamicChannelHandler.APP_NAME, appName);
		props.put(Property.DynamicChannelHandler.INET_HOST, hostname);
		props.put(Property.DynamicChannelHandler.INET_PORT, port);
		props.put(Property.DynamicChannelHandler.FACTORY_PIDS, factoryPids);
		props.put(Property.DynamicChannelHandler.HANDLER_NAMES, handlerNames);

		// store extra properties as handler properties
		extraProperties.ifPresent(ep -> props.putAll(ep));

		// bind this dynamic handler
		final String channelHandlerTarget = String.format("(&(%s=%s)(%s=%s))", Property.ChannelHandler.CHANNEL_ID,
				channelId, Property.ChannelHandler.APP_NAME, appName);
		props.put(ReferenceName.DynamicChannelHandler.CHANNEL_HANDLER_TARGET, channelHandlerTarget);

		return props;
	}

	@Override
	public String createNettyServerConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception {

		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.NettyServer.APP_NAME, appName);

		// FIXME: ldap injection
		String channelInitializerTarget = String.format("(&(%s=%s)(%s=%s)(%s=%d)%s%s)",
				Property.ChannelInitializer.APP_NAME, appName, Property.ChannelInitializer.INET_HOST, hostname,
				Property.ChannelInitializer.INET_PORT, port,
				listFilter(Property.ChannelInitializer.FACTORY_PIDS, factoryPids),
				listFilter(Property.ChannelInitializer.HANDLER_NAMES, handlerNames));
		props.put(ReferenceName.NettyServer.CHANNEL_INITIALIZER_TARGET, channelInitializerTarget);

		// Target event groups at the application level currently
		String bossGroupTarget = String.format("(&(%s=%s)(%s=%s))", Property.EventLoopGroup.APP_NAME, appName,
				Property.EventLoopGroup.GROUP_NAME, ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP);
		props.put(ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP_TARGET, bossGroupTarget);
		String workerGroupTarget = String.format("(&(%s=%s)(%s=%s))", Property.EventLoopGroup.APP_NAME, appName,
				Property.EventLoopGroup.GROUP_NAME, ReferenceName.NettyServer.WORKER_EVENT_LOOP_GROUP);
		props.put(ReferenceName.NettyServer.WORKER_EVENT_LOOP_GROUP_TARGET, workerGroupTarget);

		// Server bootstrap target
		String serverBootstrapTarget = String.format("(&(%s=%s)(%s=%s)(%s=%d))", Property.ServerBootstrap.APP_NAME,
				appName, Property.ServerBootstrap.INET_HOST, hostname, Property.ServerBootstrap.INET_PORT, port);
		props.put(ReferenceName.NettyServer.SERVER_BOOTSTRAP_TARGET, serverBootstrapTarget);

		props.put(Property.NettyServer.INET_HOST, hostname);
		props.put(Property.NettyServer.INET_PORT, port);
		props.put(Property.NettyServer.FACTORY_PIDS, factoryPids.toArray(EMPTY_ARRAY));
		props.put(Property.NettyServer.HANDLER_NAMES, handlerNames.toArray(EMPTY_ARRAY));

		// addExtraProperties(props, extraProperties);

		return createConfiguration(io.blesmol.netty.api.Configuration.NETTY_SERVER_PID, props);

	}

	private void addExtraProperties(Hashtable<String, Object> properties,
			Optional<Map<String, Object>> extraProperties) {
		if (extraProperties.isPresent()) {
			extraProperties.get().entrySet().forEach(es -> {
				properties.put(Property.EXTRA_PREFIX + es.getKey(), es.getValue());
			});
			properties.put(Property.EXTRA_PROPERTIES, true);
		}
	}

	@Override
	public String createNettyClientConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, Optional<String> serverAppName)
			throws Exception {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.NettyClient.APP_NAME, appName);

		// FIXME: ldap injection
		String channelInitializerTarget = String.format("(&(%s=%s)(%s=%s)(%s=%d)%s%s)",
				Property.ChannelInitializer.APP_NAME, appName, Property.ChannelInitializer.INET_HOST, hostname,
				Property.ChannelInitializer.INET_PORT, port,
				listFilter(Property.ChannelInitializer.FACTORY_PIDS, factoryPids),
				listFilter(Property.ChannelInitializer.HANDLER_NAMES, handlerNames));
		props.put(ReferenceName.NettyClient.CHANNEL_INITIALIZER_TARGET, channelInitializerTarget);

		// Target event group at the application level currently
		// If the server app name is present, use that instead of this app name, so as
		// to chain off the
		// server's event loop
		String eventGroupTarget = String.format("(&(%s=%s)(%s=%s))", Property.EventLoopGroup.APP_NAME,
				serverAppName.orElse(appName), Property.EventLoopGroup.GROUP_NAME,
				ReferenceName.NettyClient.EVENT_LOOP_GROUP);
		props.put(ReferenceName.NettyClient.EVENT_LOOP_GROUP_TARGET, eventGroupTarget);

		// Bootstrap target, using optional server app name too
		String bootstrapTemplate = serverAppName.isPresent() ? "(&(%s=%s)(%s=%s)(%s=%d))"
				: "(&(%s=%s)(%s=%s)(%s=%d)(%s=%s))";
		// String.format ignores extra arguments
		String bootstrapTarget = String.format(bootstrapTemplate, Property.Bootstrap.APP_NAME, appName,
				Property.Bootstrap.INET_HOST, hostname, Property.Bootstrap.INET_PORT, port,
				Property.Bootstrap.SERVER_APP_NAME, serverAppName.orElse(""));
		props.put(ReferenceName.NettyClient.BOOTSTRAP_TARGET, bootstrapTarget);

		props.put(Property.NettyClient.INET_HOST, hostname);
		props.put(Property.NettyClient.INET_PORT, port);
		props.put(Property.NettyClient.FACTORY_PIDS, factoryPids.toArray(EMPTY_ARRAY));
		props.put(Property.NettyClient.HANDLER_NAMES, handlerNames.toArray(EMPTY_ARRAY));
		props.put(Property.NettyClient.SERVER_APP_NAME, serverAppName.orElse(""));

		return createConfiguration(io.blesmol.netty.api.Configuration.NETTY_CLIENT_PID, props);
	}

	@Override
	public String createServerBootstrapProvider(String appName, String hostname, int port) throws Exception {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.ServerBootstrap.APP_NAME, appName);
		props.put(Property.ServerBootstrap.INET_HOST, hostname);
		props.put(Property.ServerBootstrap.INET_PORT, port);
		return createConfiguration(io.blesmol.netty.api.Configuration.SERVER_BOOTSTRAP_PID, props);
	}

	@Override
	public String createBootstrapProvider(String appName, String hostname, int port, Optional<String> serverAppName)
			throws Exception {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.Bootstrap.APP_NAME, appName);
		props.put(Property.Bootstrap.INET_HOST, hostname);
		props.put(Property.Bootstrap.INET_PORT, port);
		props.put(Property.Bootstrap.SERVER_APP_NAME, serverAppName.orElse(""));
		return createConfiguration(io.blesmol.netty.api.Configuration.BOOTSTRAP_PID, props);
	}

	private String listFilter(String propertyName, List<String> property) {
		return property.stream().map(s -> String.format("(%s=%s)", propertyName, s.toString()))
				.collect(Collectors.joining());
	}

	@Override
	public void deleteConfigurationPids(Collection<String> pids) throws Exception {

		for (String pid : pids) {
			final String filter = String.format("(%s=%s)", Constants.SERVICE_PID, pid);

			Configuration[] configs = admin.listConfigurations(filter);
			if (configs == null) {
				// TODO debug/trace log
				System.err.println("No configurations to delete for filter " + filter);
				continue;
			}
			for (Configuration config : configs) {
				configurations.remove(config);
				try {
					config.delete();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	public Dictionary<String, Object> toChannelHandlerProps(String appName, String handlerName, String channelId,
			Optional<Map<String, Object>> extraProperties) {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.ChannelHandler.APP_NAME, appName);
		props.put(Property.ChannelHandler.HANDLER_NAME, handlerName);
		props.put(Property.ChannelHandler.CHANNEL_ID, channelId);

		props.putAll(fromOptionalExtraProperties(extraProperties));

		return props;
	}

	@Override
	public Map<String, Object> fromOptionalExtraProperties(Optional<Map<String, Object>> extraProperties) {
		final Map<String, Object> results = new HashMap<>();
		extraProperties.ifPresent(c -> {
			results.putAll(c.entrySet().stream().filter(es -> es.getKey().startsWith(Property.EXTRA_PREFIX)).collect(
					Collectors.toMap(e -> e.getKey().substring(Property.EXTRA_PREFIX.length()), e -> e.getValue())));
		});
		return results;
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
