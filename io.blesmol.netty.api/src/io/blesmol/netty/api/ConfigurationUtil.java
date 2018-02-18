package io.blesmol.netty.api;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;

import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface ConfigurationUtil {

	String createNettyServerConfig(String appName, String hostname, Integer port, List<String> factoryPids, List<String> handlerNames)
			throws Exception;

	void deleteNettyServerConfig(String configurationPid) throws Exception;

	default Dictionary<String, Object> toChannelHandlerProps(String handlerName, String channelId) {
		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.ChannelHandler.HANDLER_NAME, handlerName);
		props.put(Property.ChannelHandler.CHANNEL_ID, channelId);

		return props;
	}
	
	default Dictionary<String, Object> toDynamicChannelHandlerProperties(String channelId, String appName,
			String hostname, int port, List<String> factoryPids, List<String> handlerNames) {
		return toDynamicChannelHandlerProperties(channelId, appName, hostname, port,
				factoryPids.toArray(new String[0]), handlerNames.toArray(new String[0]));
	}

	default Dictionary<String, Object> toDynamicChannelHandlerProperties(String channelId, String appName,
			String hostname, int port, String[] factoryPids, String[] handlerNames) {

		final Hashtable<String, Object> props = new Hashtable<>();
		props.put(Property.OsgiChannelHandler.CHANNEL_ID, channelId);
		props.put(Property.OsgiChannelHandler.APP_NAME, appName);
		props.put(Property.OsgiChannelHandler.INET_HOST, hostname);
		props.put(Property.OsgiChannelHandler.INET_PORT, port);
		props.put(Property.OsgiChannelHandler.FACTORY_PIDS, factoryPids);
		props.put(Property.OsgiChannelHandler.HANDLER_NAMES, handlerNames);

		// bind this dynamic handler
		props.put(ReferenceName.OsgiChannelHandler.CHANNEL_HANDLER,
				String.format("(%s=%s)", Property.ChannelHandler.CHANNEL_ID, channelId));

		return props;
	}
}
