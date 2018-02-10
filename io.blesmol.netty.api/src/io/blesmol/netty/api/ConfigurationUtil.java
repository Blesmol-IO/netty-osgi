package io.blesmol.netty.api;

import org.osgi.annotation.versioning.ProviderType;

@ProviderType
public interface ConfigurationUtil {

	void createApplication(String appName, String hostname, Integer port) throws Exception;
	
	void createNettyServerConfig(String appName, String hostname, Integer port) throws Exception;
	
	void deleteNettyServerConfig(String appName) throws Exception;

	void createOsgiChannelHandlerConfig(String appName) throws Exception;

	void deleteApplication(String appName) throws Exception;
	
	void deleteOsgiChannelHandlerConfig(String appName) throws Exception;
	
	void createChannelInitializerConfig(String appName) throws Exception;
	
	void deleteChannelInitializerConfig(String appName) throws Exception;

}
