package io.blesmol.netty.provider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.NettyApi;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.api.ReferenceName;

@Component(immediate = true)
public class ConfigurationUtilProvider implements ConfigurationUtil {

	private static final Logger logger = LoggerFactory.getLogger(ConfigurationUtil.class);

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
		logger.debug("Deactivating configuration utility provider");
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

	List<Configuration> getConfigurations(String factoryPid, Hashtable<String, Object> properties) throws Exception {
		final Configuration[] configurations = admin.listConfigurations(
				createFilterFromMap(ConfigurationAdmin.SERVICE_FACTORYPID, factoryPid, properties));
		return (configurations == null) ? Collections.emptyList() : Arrays.asList(configurations);
	}

	// https://stackoverflow.com/a/46008789
	// Copyright issues!
	// Maybe replace with Spring LDAP filter from spring-ldap-core? 
	// https://www.owasp.org/index.php/Preventing_LDAP_Injection_in_Java
	@Override
	public String ldapSearchEscape(String unescaped) {
		if (unescaped == null) {
			return "";
		}

		final StringBuilder sb = new StringBuilder(unescaped.length());
		for (byte c : unescaped.getBytes(StandardCharsets.UTF_8)) {
			if (c == '\\') {
				sb.append("\\5c");
			} else if (c == '*') {
				sb.append("\\2a");
			} else if (c == '(') {
				sb.append("\\28");
			} else if (c == ')') {
				sb.append("\\29");
			} else if (c == 0) {
				sb.append("\\00");
			} else if ((c & 0xff) > 127) {
				sb.append("\\").append(to2CharHexString((c & 0xff)));
			} // UTF-8's non-7-bit characters, e.g. é, á, etc...
			else {
				sb.append((char) c);
			}
		}
		return sb.toString();

	}
	private String to2CharHexString(int i)
	{
	    String s = Integer.toHexString(i & 0xff);
	    if (s.length()==1) return "0"+s;
	    else return s;
	}
	
	String toEscapedFilter(String key, Object value) {
		if (value == null) return "";

		// Ignore targets when creating a filter
		if (key.endsWith(".target")) {
			return "";
		}
		
		StringBuilder sb = new StringBuilder();
		if (value instanceof Collection) {
			Collection collection = (Collection)value;
			for (Object o : collection) {
				sb.append(String.format("(%s=%s)", ldapSearchEscape(key), toEscapedValue(o)));
			}
		} else if (value.getClass().isArray()) {
			Object[] array = (Object[]) value;
			for (Object o : array) {
				sb.append(String.format("(%s=%s)", ldapSearchEscape(key), toEscapedValue(o)));
			}
		} else {
			sb.append(String.format("(%s=%s)", ldapSearchEscape(key), toEscapedValue(value)));
		}
		return sb.toString();
	}
	
	String toEscapedValue(Object value) {
//		if (true) return value.toString();
		if (value == null) return "";

		if (value instanceof Boolean || value instanceof Integer || value instanceof String) {
			return ldapSearchEscape(value.toString());
		} else {
			logger.warn("Invalid type {} passed to toEscapedFilter via object {}", value.getClass(), value);
			return "";
		}
	}
	
	@Override
	public String createFilterFromMap(String pidKey, String pidValue, Map<String, Object> properties) {
		final StringBuilder sb = new StringBuilder("(&");
		if (pidKey != null && pidValue != null) {
			sb.append(String.format("(%s=%s)", ldapSearchEscape(pidKey), ldapSearchEscape(pidValue)));
		}
		properties.entrySet().stream().map(es -> toEscapedFilter(es.getKey(), es.getValue())).filter(f -> (f != null && !f.isEmpty()))
				.forEach(sb::append);
		sb.append(")");
		return sb.toString();
	}

//	@Override
//	public String createFilterFromDictionary(String pidKey, String pidValue, Hashtable<String, Object> properties) {
//		final Enumeration<String> keys = properties.keys();
//		final StringBuilder sb = new StringBuilder("(&");
//		if (pidKey != null && pidValue != null) {
//			sb.append(String.format("(%s=%s)", pidKey, pidValue));	
//		}
//		while (keys.hasMoreElements()) {
//			String key = keys.nextElement();
//			sb.append(String.format("(%s=%s)", key, properties.get(key)));
//		}
//		sb.append(")");
//		return sb.toString();
//
//	}

	String createConfiguration(String factoryPid, Dictionary<String, Object> properties) throws Exception {
		Configuration configuration = admin.createFactoryConfiguration(factoryPid, "?");
		configuration.update(properties);
		configurations.add(configuration);
		return configuration.getPid();
	}

	// Untested!
	@Override
	public Callable<Set<String>> getOrCreate(final List<String> factoryPids, final Map<String, Object> properties) {
		return new Callable<Set<String>>() {

			@Override
			public Set<String> call() throws Exception {

				Set<String> results = new HashSet<>();
				for (String pid : factoryPids) {
					final Hashtable<String, Object> dict = new Hashtable<>(properties);
					List<Configuration> configurations = getConfigurations(pid, dict);
					if (configurations == null || configurations.isEmpty()) {
						logger.debug("No configuration found for pid {} and properties {}, creating one.", pid, dict);
						results.add(createConfiguration(pid, dict));
					} else {
						if (configurations.size() > 1) {
							logger.error(
									"{} configurations return for factory pid {} and props {}, expected 1. Maybe supply a target to the filter?",
									configurations.size(), pid, properties);
							throw new IllegalStateException("Too many configurations");
						} else {
							logger.debug("A configuration found for pid {} and properties {}, not creating.", pid, dict);
							results.add(configurations.get(0).getPid());
						}

					}
				}
				return results;
			}
		};
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
				NettyApi.ChannelInitializer.APP_NAME, appName, NettyApi.ChannelInitializer.INET_HOST, hostname,
				NettyApi.ChannelInitializer.INET_PORT, port,
				listFilter(NettyApi.ChannelInitializer.FACTORY_PIDS, factoryPids),
				listFilter(NettyApi.ChannelInitializer.HANDLER_NAMES, handlerNames));
		props.put(ReferenceName.NettyServer.CHANNEL_INITIALIZER_TARGET, channelInitializerTarget);

		// Target event groups at the application level currently
		String bossGroupTarget = eventLoopGroupTarget(ConfigurationAdmin.SERVICE_FACTORYPID,
				NettyApi.EventLoopGroup.PID, appName, hostname, port, ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP);
		props.put(ReferenceName.NettyServer.BOSS_EVENT_LOOP_GROUP_TARGET, bossGroupTarget);
		String workerGroupTarget = eventLoopGroupTarget(ConfigurationAdmin.SERVICE_FACTORYPID,
				NettyApi.EventLoopGroup.PID, appName, hostname, port,
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
		results.addAll(createBootstrap(appName, hostname, port, factoryPids, handlerNames, extraProperties, serverAppName));
//		results.add(createEventLoopGroup(appName, hostname, port, ReferenceName.NettyClient.EVENT_LOOP_GROUP));
//		results.addAll(createChannelInitializer(appName, hostname, port, factoryPids, handlerNames, extraProperties));
		results.add(createNettyClientConfig(appName, hostname, port, factoryPids, handlerNames, extraProperties,
				serverAppName, shutdownGroup));
		return results;
	}

	@Override
	public String createNettyClientConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, Optional<String> serverAppName,
			Optional<Boolean> shutdownGroup) throws Exception {
		final Hashtable<String, Object> props = nettyClientProperties(appName, hostname, port, factoryPids,
				handlerNames, extraProperties, serverAppName, shutdownGroup);
		return createConfiguration(NettyApi.NettyClient.PID, props);
	}

	@Override
	public List<String> createBootstrap(String appName, String inetHost, int inetPort, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, Optional<String> serverAppName)
			throws Exception {
		final List<String> results = new ArrayList<>();
		results.add(createBootstrapConfig(appName, inetHost, inetPort, factoryPids, handlerNames, extraProperties, serverAppName));
		results.add(createEventLoopGroup(appName, inetHost, inetPort, NettyApi.Bootstrap.Reference.EVENT_LOOP_GROUP));
		results.addAll(createChannelInitializer(appName, inetHost, inetPort, factoryPids, handlerNames, extraProperties));

		return results;
	}
	
	@Override
	public String createBootstrapConfig(String appName, String inetHost, int inetPort, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, Optional<String> serverAppName)
			throws Exception {
		final Hashtable<String, Object> props = bootstrapProperties(appName, inetHost, inetPort, factoryPids, handlerNames, extraProperties, serverAppName);
		return createConfiguration(NettyApi.Bootstrap.PID, props);
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
		// final Hashtable<String, Object> props = eventExecutorGroupProperties(appName,
		// inetHost, inetPort, groupName);
		return createConfiguration(NettyApi.EventExecutorGroup.PID,
				eventExecutorGroupProperties(appName, inetHost, inetPort, groupName));
	}

	@Override
	public String createChannelConfig(String factoryPid, String appName, String inetHost, Integer inetPort,
			String channelId) throws Exception {
		return createConfiguration(factoryPid, channelProperties(appName, inetHost, inetPort, channelId));
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
		return createConfiguration(NettyApi.ChannelInitializer.PID, props);
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
	public Hashtable<String, Object> bootstrapProperties(String appName, String inetHost, int inetPort, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, 
			Optional<String> serverAppName) {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(NettyApi.Bootstrap.APP_NAME, appName);
		props.put(NettyApi.Bootstrap.INET_HOST, inetHost);
		props.put(NettyApi.Bootstrap.INET_PORT, inetPort);
		props.put(NettyApi.Bootstrap.SERVER_APP_NAME, serverAppName.orElse(""));
		
		String channelInitializerTarget = createFilterFromMap(ConfigurationAdmin.SERVICE_FACTORYPID, NettyApi.ChannelInitializer.PID,
				channelInitializerProperties(appName, inetHost, inetPort, factoryPids.toArray(EMPTY_ARRAY), handlerNames.toArray(EMPTY_ARRAY), extraProperties));

		// FIXME: ldap injection
////		String channelInitializerTarget = String.format("(&(%s=%s)(%s=%s)(%s=%d)%s%s)",
////				NettyApi.ChannelInitializer.APP_NAME, appName, NettyApi.ChannelInitializer.INET_HOST, hostname,
////				NettyApi.ChannelInitializer.INET_PORT, port,
////				listFilter(NettyApi.ChannelInitializer.FACTORY_PIDS, factoryPids),
////				listFilter(NettyApi.ChannelInitializer.HANDLER_NAMES, handlerNames));
//		props.put(ReferenceName.NettyClient.CHANNEL_INITIALIZER_TARGET, channelInitializerTarget);
		props.put(NettyApi.Bootstrap.Reference.CHANNEL_INITIALIZER_TARGET, channelInitializerTarget);

		// Target event group
		String eventGroupTarget = eventLoopGroupTarget(ConfigurationAdmin.SERVICE_FACTORYPID,
				NettyApi.EventLoopGroup.PID, appName, inetHost, inetPort, NettyApi.Bootstrap.Reference.EVENT_LOOP_GROUP);
		props.put(NettyApi.Bootstrap.Reference.EVENT_LOOP_GROUP_TARGET, eventGroupTarget);

		
		return props;
	}

	@Override
	public Hashtable<String, Object> channelHandlerProperties(String appName, String inetHost, int inetPort,
			String handlerName, String channelId, Optional<Map<String, Object>> extraProperties) {
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
		props.put(NettyApi.ChannelInitializer.APP_NAME, appName);
		props.put(NettyApi.ChannelInitializer.INET_HOST, hostname);
		props.put(NettyApi.ChannelInitializer.INET_PORT, port);
		props.put(NettyApi.ChannelInitializer.FACTORY_PIDS, factoryPids);
		props.put(NettyApi.ChannelInitializer.HANDLER_NAMES, handlerNames);

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
	public Hashtable<String, Object> eventExecutorGroupProperties(String appName, String inetHost, Integer inetPort,
			String groupName) {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(NettyApi.EventExecutorGroup.APP_NAME, appName);
		props.put(NettyApi.EventExecutorGroup.INET_HOST, inetHost);
		props.put(NettyApi.EventExecutorGroup.INET_PORT, inetPort);
		props.put(NettyApi.EventExecutorGroup.GROUP_NAME, groupName);
		return props;
	}

	@Override
	public Hashtable<String, Object> eventLoopGroupProperties(String appName, String inetHost, Integer inetPort,
			String groupName) {
		final Hashtable<String, Object> eventLoopProperties = new Hashtable<>();
		eventLoopProperties.put(NettyApi.EventLoopGroup.APP_NAME, appName);
		// Only target on app name for now. That should become "userInfo"
		// eventLoopProperties.put(NettyApi.EventLoopGroup.INET_HOST, inetHost);
		// eventLoopProperties.put(NettyApi.EventLoopGroup.INET_PORT, inetPort);
		eventLoopProperties.put(NettyApi.EventLoopGroup.GROUP_NAME, groupName);
		return eventLoopProperties;
	}

	@Override
	public Hashtable<String, Object> nettyClientProperties(String appName, String inetHost, Integer inetPort,
			List<String> factoryPids, List<String> handlerNames, Optional<Map<String, Object>> extraProperties,
			Optional<String> serverAppName, Optional<Boolean> shutdownGroup) {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(NettyApi.NettyClient.APP_NAME, appName);

		String bootstrapTarget = createFilterFromMap(ConfigurationAdmin.SERVICE_FACTORYPID,
				NettyApi.Bootstrap.PID, bootstrapProperties(appName, inetHost, inetPort, factoryPids, handlerNames, extraProperties, serverAppName));

		props.put(ReferenceName.NettyClient.BOOTSTRAP_TARGET, bootstrapTarget);

		props.put(NettyApi.NettyClient.INET_HOST, inetHost);
		props.put(NettyApi.NettyClient.INET_PORT, inetPort);
		props.put(NettyApi.NettyClient.FACTORY_PIDS, factoryPids.toArray(EMPTY_ARRAY));
		props.put(NettyApi.NettyClient.HANDLER_NAMES, handlerNames.toArray(EMPTY_ARRAY));
		props.put(NettyApi.NettyClient.SERVER_APP_NAME, serverAppName.orElse(""));
		props.put(NettyApi.NettyClient.SHUTDOWN_GROUP, shutdownGroup.orElse(true));
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

	// TARGETS

	@Override
	public String channelTarget(String appName, String inetHost, Integer inetPort, String channelId) {
		return createFilterFromMap(null, null, channelProperties(appName, inetHost, inetPort, channelId));
	}

	@Override
	public String eventLoopGroupTarget(String pidKey, String pidValue, String appName, String inetHost,
			Integer inetPort, String groupName) {
		return createFilterFromMap(pidKey, pidValue,
				eventLoopGroupProperties(appName, inetHost, inetPort, groupName));
	}

}
