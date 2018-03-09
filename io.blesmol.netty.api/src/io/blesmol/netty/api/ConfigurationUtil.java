package io.blesmol.netty.api;

import java.util.Collection;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.osgi.annotation.versioning.ProviderType;
import org.osgi.service.cm.Configuration;

// TODO: consider a builder pattern
@ProviderType
public interface ConfigurationUtil {

	String[] EMPTY_ARRAY = new String[0];

	default String createNettyServerConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames) throws Exception {
		return createNettyServerConfig(appName, hostname, port, factoryPids, handlerNames, Optional.empty());
	}

	default Dictionary<String, Object> toDynamicChannelHandlerProperties(String channelId, String appName,
			String hostname, int port, List<String> factoryPids, List<String> handlerNames,
			Optional<Map<String, Object>> extraProperties) {
		return dynamicHandlerProperties(channelId, appName, hostname, port, factoryPids.toArray(EMPTY_ARRAY),
				handlerNames.toArray(EMPTY_ARRAY), extraProperties);
	}

	default Dictionary<String, Object> toChannelInitializerProperties(String appName, String hostname, int port,
			List<String> factoryPids, List<String> handlerNames, Optional<Map<String, Object>> extraProperties) {
		return channelInitializerProperties(appName, hostname, port, factoryPids.toArray(EMPTY_ARRAY),
				handlerNames.toArray(EMPTY_ARRAY), extraProperties);
	}

	// CREATE

	List<String> createNettyServer(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;

	String createNettyServerConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;

	List<String> createNettyClient(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, Optional<String> serverAppName,
			Optional<Boolean> shutdownGroup) throws Exception;

	String createNettyClientConfig(String appName, String hostname, Integer port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties, Optional<String> serverAppName,
			Optional<Boolean> shutdownGroup) throws Exception;

	String createServerBootstrapProvider(String appName, String hostname, int port) throws Exception;

	String createBootstrapProvider(String appName, String hostname, int port, Optional<String> serverAppName)
			throws Exception;

	String createEventLoopGroup(String appName, String inetHost, Integer inetPort, String groupName) throws Exception;

	String createEventExecutorGroup(String appName, String inetHost, Integer inetPort, String groupName)
			throws Exception;

	/**
	 * Requires a factory pid since channels are created by netty and registered later
	 */
	String createChannelConfig(String factoryPid, String appName, String inetHost, Integer inetPort, String channelId) throws Exception;

	List<String> createChannelInitializer(String appName, String hostname, int port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;

	String createChannelInitializerConfig(String appName, String hostname, int port, List<String> factoryPids,
			List<String> handlerNames, Optional<Map<String, Object>> extraProperties) throws Exception;

	String createDynamicChannelHandlerConfig(String channelId, String appName, String hostname, int port,
			List<String> factoryPids, List<String> handlerNames, Optional<Map<String, Object>> extraProperties)
			throws Exception;

	// GET

	List<Configuration> getEventLoopGroupConfigurations(String appName, String inetHost, Integer inetPort,
			String groupName) throws Exception;

	// DELETE

	void deleteConfigurationPids(Collection<String> pids) throws Exception;

	// PROPERTIES

	Hashtable<String, Object> channelHandlerProperties(String appName, String inetHost, int inetPort,
			String handlerName, String channelId, Optional<Map<String, Object>> extraProperties);

	Hashtable<String, Object> channelInitializerProperties(String appName, String hostname, int port,
			String[] factoryPids, String[] handlerNames, Optional<Map<String, Object>> extraProperties);

	Hashtable<String, Object> channelProperties(String appName, String inetHost, Integer inetPort, String channelId);

	Hashtable<String, Object> dynamicHandlerProperties(String channelId, String appName, String hostname, int port,
			String[] factoryPids, String[] handlerNames, Optional<Map<String, Object>> extraProperties);

	Hashtable<String, Object> eventLoopGroupProperties(String appName, String inetHost, Integer inetPort,
			String groupName);

	Optional<Map<String, Object>> toOptionalExtraProperties(Map<String, Object> properties);

	Map<String, Object> fromOptionalExtraProperties(Optional<Map<String, Object>> extraProperties);

	// TARGETS

	String channelTarget(String appName, String inetHost, Integer inetPort, String channelId);

	String eventLoopGroupTarget(String pidKey, String pidValue, String appName, String inetHost, Integer inetPort, String groupName);

}
