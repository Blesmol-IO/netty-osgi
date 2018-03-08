package io.blesmol.netty.provider;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
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
import io.blesmol.netty.api.NettyApi;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.api.ReferenceName;

@Component(immediate = true)
public class ConfigurationUtilProvider implements ConfigurationUtil {

	private final AtomicBoolean deactivated = new AtomicBoolean(false);

	// Is this needed?
	final List<Configuration> configurations = new CopyOnWriteArrayList<>();

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

	// PACKAGE PUBLIC (for testing)

	void addExtraProperties(Hashtable<String, Object> properties, Optional<Map<String, Object>> extraProperties) {
		if (extraProperties.isPresent()) {
			extraProperties.get().entrySet().forEach(es -> {
				properties.put(Property.EXTRA_PREFIX + es.getKey(), es.getValue());
			});
			properties.put(Property.EXTRA_PROPERTIES, true);
		}
	}

	String listFilter(String propertyName, List<String> property) {
		return property.stream().map(s -> String.format("(%s=%s)", propertyName, s.toString()))
				.collect(Collectors.joining());
	}

	List<Configuration> getConfigurations(String factoryPid, Dictionary<String, Object> properties) throws Exception {
		final Configuration[] configurations = admin
				.listConfigurations(createFilterFromDictionary(ConfigurationAdmin.SERVICE_FACTORYPID, factoryPid, properties));
		return (configurations == null) ? Collections.emptyList() : Arrays.asList(configurations);
	}

	// FIXME: No LDAP escaping perform
	String createFilterFromDictionary(String pidKey, String pidValue, Dictionary<String, Object> properties) {
		final Enumeration<String> keys = properties.keys();
		final StringBuilder sb = new StringBuilder("(&");
		sb.append(String.format("(%s=%s)", pidKey, pidValue));
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			sb.append(String.format("(%s=%s)", key, properties.get(key)));
		}
		sb.append(")");
		return sb.toString();

	}

	String createConfiguration(String factoryPid, Dictionary<String, Object> properties) throws Exception {
		Configuration configuration = admin.createFactoryConfiguration(factoryPid, "?");
		configuration.update(properties);
		configurations.add(configuration);
		return configuration.getPid();
	}

	// CREATE

	@Override
	public List<String> createNettyServer(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception {

		final List<String> results = new ArrayList<>();
		results.add(createServerBootstrapProvider(appName, hostname, port));
		results.add(createEventLoopGroup(appName, hostname, port, ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP));
		results.add(createEventLoopGroup(appName, hostname, port, ReferenceName.NettyServer.WORKER_EVENT_LOOP_GROUP));
		results.addAll(createChannelInitializer(appName, hostname, port, factoryPids, handlerNames, extraProperties));
		results.add(createNettyServerConfig(appName, hostname, port, factoryPids, handlerNames, extraProperties));
		return results;

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
		String bossGroupTarget = eventLoopGroupTarget(ConfigurationAdmin.SERVICE_FACTORYPID, NettyApi.EventLoopGroup.PID, appName, hostname, port,
				ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP);
		props.put(ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP_TARGET, bossGroupTarget);
		String workerGroupTarget = eventLoopGroupTarget(ConfigurationAdmin.SERVICE_FACTORYPID, NettyApi.EventLoopGroup.PID, appName, hostname, port,
				ReferenceName.NettyServer.WORKER_EVENT_LOOP_GROUP);
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

	@Override
	public List<String> createNettyClient(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, Optional<String> serverAppName,
			Optional<Boolean> shutdownGroup) throws Exception {

		final List<String> results = new ArrayList<>();
		results.add(createBootstrapProvider(appName, hostname, port, serverAppName));
		results.add(createEventLoopGroup(appName, hostname, port, ReferenceName.NettyClient.EVENT_LOOP_GROUP));
		results.addAll(createChannelInitializer(appName, hostname, port, factoryPids, handlerNames, extraProperties));
		results.add(createNettyClientConfig(appName, hostname, port, factoryPids, handlerNames, extraProperties,
				serverAppName, shutdownGroup));
		return results;
	}

	@Override
	public String createNettyClientConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, Optional<String> serverAppName,
			Optional<Boolean> shutdownGroup) throws Exception {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(NettyApi.NettyClient.APP_NAME, appName);

		// FIXME: ldap injection
		String channelInitializerTarget = String.format("(&(%s=%s)(%s=%s)(%s=%d)%s%s)",
				Property.ChannelInitializer.APP_NAME, appName, Property.ChannelInitializer.INET_HOST, hostname,
				Property.ChannelInitializer.INET_PORT, port,
				listFilter(Property.ChannelInitializer.FACTORY_PIDS, factoryPids),
				listFilter(Property.ChannelInitializer.HANDLER_NAMES, handlerNames));
		props.put(ReferenceName.NettyClient.CHANNEL_INITIALIZER_TARGET, channelInitializerTarget);

		// Target event group
		String eventGroupTarget = eventLoopGroupTarget(ConfigurationAdmin.SERVICE_FACTORYPID, NettyApi.EventLoopGroup.PID, appName, hostname, port,
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

		props.put(NettyApi.NettyClient.INET_HOST, hostname);
		props.put(NettyApi.NettyClient.INET_PORT, port);
		props.put(NettyApi.NettyClient.FACTORY_PIDS, factoryPids.toArray(EMPTY_ARRAY));
		props.put(NettyApi.NettyClient.HANDLER_NAMES, handlerNames.toArray(EMPTY_ARRAY));
		props.put(NettyApi.NettyClient.SERVER_APP_NAME, serverAppName.orElse(""));
		props.put(NettyApi.NettyClient.SHUTDOWN_GROUP, shutdownGroup.orElse(true));
		return createConfiguration(NettyApi.NettyClient.PID, props);
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

	@Override
	public String createServerBootstrapProvider(String appName, String hostname, int port) throws Exception {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.ServerBootstrap.APP_NAME, appName);
		props.put(Property.ServerBootstrap.INET_HOST, hostname);
		props.put(Property.ServerBootstrap.INET_PORT, port);
		return createConfiguration(io.blesmol.netty.api.Configuration.SERVER_BOOTSTRAP_PID, props);
	}

	@Override
	public String createEventLoopGroup(String appName, String inetHost, Integer inetPort, String groupName)
			throws Exception {
		final Hashtable<String, Object> props = eventLoopGroupProperties(appName, inetHost, inetPort, groupName);
		return createConfiguration(NettyApi.EventLoopGroup.PID, props);
	}

	@Override
	public String createEventExecutorGroup(String appName, String inetHost, Integer inetPort, String groupName)
			throws Exception {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(NettyApi.EventExecutorGroup.APP_NAME, appName);
		props.put(NettyApi.EventExecutorGroup.INET_HOST, inetHost);
		props.put(NettyApi.EventExecutorGroup.INET_PORT, inetPort);
		props.put(NettyApi.EventExecutorGroup.GROUP_NAME, groupName);
		return createConfiguration(NettyApi.EventExecutorGroup.PID, props);
	}

	@Override
	public List<String> createChannelInitializer(String appName, String hostname, int port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception {

		final List<String> results = new ArrayList<>();
		results.add(createEventExecutorGroup(appName, hostname, port,
				ReferenceName.ChannelInitializer.EVENT_EXECUTOR_GROUP));
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
	public String createDynamicChannelHandlerConfig(String channelId, String appName, String hostname, int port,
			List<String> factoryPids, List<String> handlerNames, Optional<Map<String, Object>> extraProperties)
			throws Exception {

		final Dictionary<String, Object> props = toDynamicChannelHandlerProperties(channelId, appName, hostname, port,
				factoryPids, handlerNames, extraProperties);
		return createConfiguration(io.blesmol.netty.api.Configuration.DYNAMIC_CHANNEL_HANDLER_PID, props);
	}

	// GET

	@Override
	public List<Configuration> getEventLoopGroupConfigurations(String appName, String inetHost, Integer inetPort,
			String groupName) throws Exception {
		final Hashtable<String, Object> props = eventLoopGroupProperties(appName, inetHost, inetPort, groupName);
		return getConfigurations(NettyApi.EventLoopGroup.PID, props);
	}

	// DELETE

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

	// PROPERTIES

	@Override
	public Hashtable<String, Object> channelHandlerProperties(String appName, String inetHost, int inetPort, String handlerName, String channelId,
			Optional<Map<String, Object>> extraProperties) {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(NettyApi.ChannelHandler.APP_NAME, appName);
		props.put(NettyApi.ChannelHandler.INET_HOST, inetHost);
		props.put(NettyApi.ChannelHandler.INET_PORT, inetPort);
		props.put(NettyApi.ChannelHandler.HANDLER_NAME, handlerName);
		props.put(NettyApi.ChannelHandler.CHANNEL_ID, channelId);
		props.putAll(fromOptionalExtraProperties(extraProperties));
		return props;
	}

	@Override
	public Hashtable<String, Object> channelInitializerProperties(String appName, String hostname, int port,
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
				appName, Property.DynamicChannelHandler.INET_HOST, hostname, Property.DynamicChannelHandler.INET_PORT,
				port);
		props.put(ReferenceName.ChannelInitializer.CHANNEL_HANDLERS_TARGET, handlerTarget);

		// Target event executor group
		final String eventTarget = String.format("(&(%s=%s)(%s=%s))", Property.EventExecutorGroup.APP_NAME, appName,
				Property.EventExecutorGroup.GROUP_NAME, ReferenceName.ChannelInitializer.EVENT_EXECUTOR_GROUP);
		props.put(ReferenceName.ChannelInitializer.EVENT_EXECUTOR_GROUP_TARGET, eventTarget);

		return props;
	}

	@Override
	public Hashtable<String, Object> channelProperties(String appName, String inetHost, Integer inetPort,
			String channelId) {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(NettyApi.Channel.APP_NAME, appName);
		props.put(NettyApi.Channel.INET_HOST, inetHost);
		props.put(NettyApi.Channel.INET_PORT, inetPort);
		props.put(NettyApi.Channel.CHANNEL_ID, channelId);
		return props;
	}

	@Override
	public Hashtable<String, Object> dynamicHandlerProperties(String channelId, String appName, String hostname,
			int port, String[] factoryPids, String[] handlerNames, Optional<Map<String, Object>> extraProperties) {

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
	public Hashtable<String, Object> eventLoopGroupProperties(String appName, String inetHost, Integer inetPort,
			String groupName) {
		final Hashtable<String, Object> eventLoopProperties = new Hashtable<>();
		eventLoopProperties.put(NettyApi.EventLoopGroup.APP_NAME, appName);
		eventLoopProperties.put(NettyApi.EventLoopGroup.INET_HOST, inetHost);
		eventLoopProperties.put(NettyApi.EventLoopGroup.INET_PORT, inetPort);
		eventLoopProperties.put(NettyApi.EventLoopGroup.GROUP_NAME, groupName);
		return eventLoopProperties;
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

	// TARGETS

	@Override
	public String channelTarget(String pidKey, String pidValue, String appName, String inetHost, Integer inetPort, String channelId) {
		return createFilterFromDictionary(pidKey, pidValue, channelProperties(appName, inetHost, inetPort, channelId));
	}
	
	@Override
	public String eventLoopGroupTarget(String pidKey, String pidValue, String appName, String inetHost, Integer inetPort, String groupName) {
		return createFilterFromDictionary(pidKey, pidValue, eventLoopGroupProperties(appName, inetHost, inetPort, groupName));
	}

}
