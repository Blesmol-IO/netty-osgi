package io.blesmol.netty.provider;

import java.io.IOException;
import java.util.Dictionary;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.osgi.service.component.annotations.ReferenceScope;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.OsgiChannelHandler;
import io.blesmol.netty.api.Property;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;

@Component(service = ChannelInitializer.class,  configurationPid = Configuration.CHANNEL_INITIALIZER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class ChannelInitializerProvider extends ChannelInitializer<Channel> {

	// Cross-thread access
	private volatile Configuration.ChannelInitializer config;

	private final AtomicBoolean deactivated = new AtomicBoolean(false);
	private final Map<String, org.osgi.service.cm.Configuration> configurations = new ConcurrentHashMap<>();
	private final Map<String, Channel> channels = new ConcurrentHashMap<>();
	private volatile Optional<Map<String, Object>> extraProperties;

	@Reference(scope = ReferenceScope.PROTOTYPE, policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE)
	void setOsgiChannelHandler(OsgiChannelHandler dynamicHandler, Map<String, Object> properties) {

		final String channelId = (String) properties.get(Property.OsgiChannelHandler.CHANNEL_ID);
		if (channelId == null) {
			// TODO: log
			System.err.println(String.format("Ignoring dynamic handler '%s' with null channel ID, with properties\n%s",
					dynamicHandler, properties));
			return;
		}

		final Channel ch = channels.remove(channelId);
		if (ch == null) {
			// TODO: log debug
			System.err.println(String.format("Ignoring dynamic handler '%s' with null channel ID, with properties\n%s",
					dynamicHandler, properties));
			return;
		}

		ch.pipeline().addFirst(OsgiChannelHandler.HANDLER_NAME, dynamicHandler);
		// TODO: log trace
		System.out.println(
				String.format("Added dynamic handler '%s' to channel with ID '%s'.", dynamicHandler, channelId));

		final org.osgi.service.cm.Configuration configuration = configurations.remove(channelId);
		if (configuration == null) {
			// TODO: log debug
			System.out
					.println(String.format("No configuration with channel ID '%s' for handler '%s' and properties\n%s",
							channelId, dynamicHandler, properties));
			return;
		}

		// Delete the configuration when this channel is closed
		ch.closeFuture().addListener(new ChannelFutureListener() {
			@Override
			public void operationComplete(ChannelFuture future) throws Exception {
				// Delete config
				try {
					// TODO log trace
					System.out.println(
							String.format("Deleting the dynamic handler configuration for channel ID '%s'", channelId));
					configuration.delete();
				} catch (IllegalStateException | IOException e) {
					if (deactivated.get()) {
						// In the process of deactivating, ignore error
					} else {
						// TODO: log info
						System.err.println(String.format(
								"Error in deleting dynamic handler configuration with channel ID '%s'", channelId));
						e.printStackTrace();
					}
				}

			}
		});

	}

	// Do nothing
	void unsetOsgiChannelHandler(OsgiChannelHandler dynamicHandler, Map<String, Object> properties) {
	}

	@Reference
	ConfigurationAdmin configAdmin;

	@Reference
	ConfigurationUtil configUtil;

	@Activate
	void activate(Configuration.ChannelInitializer config, Map<String, Object> props) {
		this.config = config;
		// hackish, consider prefixing the keys maybe?

		extraProperties = configUtil.toOptionalExtraProperties(props);
	}

	@Deactivate
	void deactivate() {
		if (deactivated.getAndSet(true) == true) {
			return;
		}
		// TODO log debug
		System.out.println(
				String.format("Deactivating channel initializer. Current configurations:\n%s\nCurrent channels:\n%s",
						configurations, channels));
	}

	@Override
	protected void initChannel(Channel ch) throws Exception {

		// Create and cache configuration
		final String channelId = ch.id().asLongText();
		org.osgi.service.cm.Configuration dynamicHandlerConfig = configAdmin
				.createFactoryConfiguration(io.blesmol.netty.api.Configuration.OSGI_CHANNEL_HANDLER_PID, "?");
		configurations.put(channelId, dynamicHandlerConfig);
		channels.put(channelId, ch);

		// Update the config and cross our fingers
		final Dictionary<String, Object> props = configUtil.toDynamicChannelHandlerProperties(channelId,
				config.appName(), config.inetHost(), config.inetPort(), config.factoryPids(), config.handlerNames(), extraProperties);

		dynamicHandlerConfig.update(props);
		
		System.out.println("Initialized channel handler " + this);
		
	}

}
