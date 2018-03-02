package io.blesmol.netty.api;

import java.util.Collection;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.osgi.annotation.versioning.ProviderType;

// TODO: consider a builder pattern
@ProviderType
public interface ConfigurationUtil {

	List<String> createNettyServer(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;

	String createNettyServerConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;

	List<String> createNettyClient(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, Optional<String> serverAppName, Optional<Boolean> shutdownGroup) throws Exception;

	String createNettyClientConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, Optional<String> serverAppName, Optional<Boolean> shutdownGroup) throws Exception;

	String createEventLoopGroup(String appName, String inetHost, Integer inetPort, String groupName) throws Exception;

	String createEventExecutorGroup(String appName, String inetHost, Integer inetPort, String groupName) throws Exception;

	List<String> createChannelInitializer(String appName, String hostname, int port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;
	
	String createChannelInitializerConfig(String appName, String hostname, int port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;

	String createDynamicChannelHandlerConfig(String channelId, String appName, String hostname,
			int port, List<String> factoryPids, List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;

	String createBootstrapProvider(String appName, String hostname, int port, Optional<String> serverAppName) throws Exception;
	
	String createServerBootstrapProvider(String appName, String hostname, int port) throws Exception;
	
	void deleteConfigurationPids(Collection<String> pids) throws Exception;

	Dictionary<String, Object> toChannelHandlerProps(String appName, String handlerName, String channelId,
			Optional<Map<String, Object>> extraProperties);

	Dictionary<String, Object> toDynamicChannelHandlerProperties(String channelId, String appName, String hostname,
			int port, String[] factoryPids, String[] handlerNames, Optional<Map<String, Object>> extraProperties);

	Dictionary<String, Object> toChannelInitializerProperties(String appName, String hostname, int port,
			String[] factoryPids, String[] handlerNames, Optional<Map<String, Object>> extraProperties);

	Optional<Map<String, Object>> toOptionalExtraProperties(Map<String, Object> properties);
	
	Map<String, Object> fromOptionalExtraProperties(Optional<Map<String, Object>> extraProperties);

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
