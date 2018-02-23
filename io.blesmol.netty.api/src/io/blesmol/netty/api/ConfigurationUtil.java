package io.blesmol.netty.api;

import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface ConfigurationUtil {

	
	String[] createNettyServer(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;
	
	String createNettyServerConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;

	void deleteConfigurationPid(String... configurationPids) throws Exception;

	String createEventLoopGroup(String appName, String groupName) throws Exception;
	
	Dictionary<String, Object> toChannelHandlerProps(String handlerName, String channelId,
			Optional<Map<String, Object>> extraProperties);

	Dictionary<String, Object> toDynamicChannelHandlerProperties(String channelId, String appName,
			String hostname, int port, String[] factoryPids, String[] handlerNames,
			Optional<Map<String, Object>> extraProperties);

	Dictionary<String, Object> toChannelInitializerProperties(String appName, String hostname, int port,
			String[] factoryPids, String[] handlerNames, Optional<Map<String, Object>> extraProperties);

	Optional<Map<String, Object>> toOptionalExtraProperties(Map<String, Object> properties);

	String[] EMPTY_ARRAY = new String[0];

	default String createNettyServerConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames) throws Exception {
		return createNettyServerConfig(appName, hostname, port, factoryPids, handlerNames, Optional.empty());
	}

	default Dictionary<String, Object> toDynamicChannelHandlerProperties(String channelId, String appName,
			String hostname, int port, List<String> factoryPids, List<String> handlerNames,
			Optional<Map<String, Object>> extraProperties) {
		return toDynamicChannelHandlerProperties(channelId, appName, hostname, port, factoryPids.toArray(EMPTY_ARRAY),
				handlerNames.toArray(EMPTY_ARRAY), extraProperties);
	}
	
	default Dictionary<String, Object> toChannelInitializerProperties(String appName, String hostname, int port,
			List<String> factoryPids, List<String> handlerNames, Optional<Map<String, Object>> extraProperties) {
		return toChannelInitializerProperties(appName, hostname, port, factoryPids.toArray(EMPTY_ARRAY),
				handlerNames.toArray(EMPTY_ARRAY), extraProperties);
	}

}
