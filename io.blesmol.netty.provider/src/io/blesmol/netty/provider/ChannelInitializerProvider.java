package io.blesmol.netty.provider;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.osgi.framework.Constants;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.ConfigurationUtil;
import io.blesmol.netty.api.DynamicChannelHandler;
import io.blesmol.netty.api.Property;
import io.blesmol.netty.api.ReferenceName;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.util.concurrent.EventExecutorGroup;

@Component(service = ChannelInitializer.class, configurationPid = Configuration.CHANNEL_INITIALIZER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class ChannelInitializerProvider extends ChannelInitializer<Channel> {

	// Cross-thread access
	private volatile Configuration.ChannelInitializer config;

	private final AtomicBoolean deactivated = new AtomicBoolean(false);
	private final Map<String, String> configurations = new ConcurrentHashMap<>();
	private final Map<String, Channel> channels = new ConcurrentHashMap<>();
	private volatile Optional<Map<String, Object>> extraProperties;
	private final Map<String, Object> properties = new ConcurrentHashMap<>();

	@Reference(name = ReferenceName.ChannelInitializer.EVENT_EXECUTOR_GROUP)
	EventExecutorGroup eventExecutorGroup;

	@Reference(policy = ReferencePolicy.DYNAMIC, policyOption = ReferencePolicyOption.GREEDY, cardinality = ReferenceCardinality.MULTIPLE, name = ReferenceName.ChannelInitializer.CHANNEL_HANDLERS)
	void setDynamicChannelHandler(DynamicChannelHandler dynamicHandler, Map<String, Object> properties) {

		final String channelId = (String) properties.get(Property.DynamicChannelHandler.CHANNEL_ID);
		if (channelId == null) {
			// TODO: log
			System.err.println(String.format("Ignoring dynamic handler '%s' with null channel ID, with properties\n%s",
					dynamicHandler, properties));
			return;
		}

		final Channel ch = channels.remove(channelId);
		if (ch == null) {
			// TODO: log debug
			System.err.println(String.format("Ignoring dynamic handler '%s' with null channel, with properties\n%s",
					dynamicHandler, properties));
			return;
		}

		// Run the dynamic handler on a separate event executor group so as to catch
		// all channel activity whilst the handler is being added (but not fully added).
		// Refer to the implementation details in
		// io.netty.channel.AbstractChannelHandlerContext.invokeHandler()
		ch.pipeline().addFirst(eventExecutorGroup, DynamicChannelHandler.HANDLER_NAME, dynamicHandler);

		// TODO: log trace
		System.out.println(String.format("Added '%s' to channel with ID '%s'.", dynamicHandler, channelId));

		String configurationPid = configurations.remove(channelId);
		if (configurationPid == null) {
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
					configUtil.deleteConfigurationPids(Stream.of(configurationPid).collect(Collectors.toList()));
				} catch (Exception e) {
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
	void unsetDynamicChannelHandler(DynamicChannelHandler dynamicHandler, Map<String, Object> properties) {
	}

	@Reference
	ConfigurationAdmin configAdmin;

	@Reference
	ConfigurationUtil configUtil;

	@Activate
	void activate(Configuration.ChannelInitializer config, Map<String, Object> props) {
		this.config = config;
		this.properties.putAll(props);
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
		channels.put(channelId, ch);

		final String configurationPid = configUtil.createDynamicChannelHandlerConfig(channelId, config.appName(),
				config.inetHost(), config.inetPort(), Arrays.asList(config.factoryPids()),
				Arrays.asList(config.handlerNames()), extraProperties);
		// org.osgi.service.cm.Configuration dynamicHandlerConfig = configAdmin
		// .createFactoryConfiguration(io.blesmol.netty.api.Configuration.DYNAMIC_CHANNEL_HANDLER_PID,
		// "?");
		configurations.put(channelId, configurationPid);

		// Update the config and cross our fingers
		// final Dictionary<String, Object> props =
		// configUtil.toDynamicChannelHandlerProperties(channelId,
		// config.appName(), config.inetHost(), config.inetPort(), config.factoryPids(),
		// config.handlerNames(), extraProperties);

		// dynamicHandlerConfig.update(props);

		System.out.println("Initialized channel handler " + this);

	}

	@Override
	public String toString() {
		return String.format("ChannelInitializerProvider [service.pid=%s]", properties.get(Constants.SERVICE_PID));
	}

}
