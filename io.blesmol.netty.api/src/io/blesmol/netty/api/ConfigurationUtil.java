package io.blesmol.netty.api;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface ConfigurationUtil {

	default String createNettyServerConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames) throws Exception {
		return createNettyServerConfig(appName, hostname, port, factoryPids, handlerNames, Optional.empty());
	}

	String createNettyServerConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;

	void deleteNettyServerConfig(String configurationPid) throws Exception;

	default Dictionary<String, Object> toChannelHandlerProps(String handlerName, String channelId,
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

	default Dictionary<String, Object> toDynamicChannelHandlerProperties(String channelId, String appName,
			String hostname, int port, List<String> factoryPids, List<String> handlerNames,
			Optional<Map<String, Object>> extraProperties) {
		return toDynamicChannelHandlerProperties(channelId, appName, hostname, port, factoryPids.toArray(new String[0]),
				handlerNames.toArray(new String[0]), extraProperties);
	}

	default Dictionary<String, Object> toDynamicChannelHandlerProperties(String channelId, String appName,
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
		props.put(ReferenceName.OsgiChannelHandler.CHANNEL_HANDLER,
				String.format("(%s=%s)", Property.ChannelHandler.CHANNEL_ID, channelId));

		return props;
	}

	default Dictionary<String, Object> toChannelInitializerProperties(String appName, String hostname, int port,
			List<String> factoryPids, List<String> handlerNames, Optional<Map<String, Object>> extraProperties) {
		return toChannelInitializerProperties(appName, hostname, port, factoryPids.toArray(new String[0]),
				handlerNames.toArray(new String[0]), extraProperties);
	}

	default Dictionary<String, Object> toChannelInitializerProperties(String appName, String hostname, int port,
			String[] factoryPids, String[] handlerNames, Optional<Map<String, Object>> extraProperties) {

		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.ChannelInitializer.APP_NAME, appName);
		props.put(Property.ChannelInitializer.INET_HOST, hostname);
		props.put(Property.ChannelInitializer.INET_PORT, port);
		props.put(Property.ChannelInitializer.FACTORY_PIDS, factoryPids);
		props.put(Property.ChannelInitializer.HANDLER_NAMES, handlerNames);

		// store extra properties as handler properties
		extraProperties.ifPresent(ep -> props.putAll(ep));

		props.put(ReferenceName.ChannelInitializer.CHANNEL_HANDLER_FACTORY,
				String.format("(&(%s=%s)(%s=%s)(%s=%d))", Property.ChannelInitializer.APP_NAME, appName,
						Property.ChannelInitializer.INET_HOST, hostname, Property.ChannelInitializer.INET_PORT, port));
		return props;
	}

	default Optional<Map<String, Object>> toOptionalExtraProperties(Map<String, Object> properties) {
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
