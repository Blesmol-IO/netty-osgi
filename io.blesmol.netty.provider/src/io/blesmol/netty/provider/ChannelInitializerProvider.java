package io.blesmol.netty.provider;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
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

@Component(service = ChannelInitializer.class, configurationPid = Configuration.CHANNEL_INITIALIZER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE)
public class ChannelInitializerProvider extends ChannelInitializer<Channel> {

	// Set in activate
	private String pid;
	private String appName;
	private String inetHost;
	private int inetPort;
	
	// Cross-thread access
	private volatile Configuration.ChannelInitializer config;

	private final AtomicBoolean deactivated = new AtomicBoolean(false);
	private final Map<String, String> configurations = new ConcurrentHashMap<>();
	private final List<String> configurationPids = new CopyOnWriteArrayList<>();
	private final Map<String, Channel> channels = new ConcurrentHashMap<>();
	private volatile Optional<Map<String, Object>> extraProperties;

	@Reference
	ExecutorService executorService;

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
		System.out.println(String.format("Added '%s', channel: '%s'", dynamicHandler, channelId));

		String configurationPid = configurations.remove(channelId);
		if (configurationPid == null) {
			// TODO: log debug
			System.err
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
		System.out.println("Unsetting dynamic handler " + dynamicHandler);
	}

	@Reference
	ConfigurationAdmin configAdmin;

	@Reference
	ConfigurationUtil configUtil;

	@Activate
	void activate(Configuration.ChannelInitializer config, Map<String, Object> props) {
		this.config = config;
		this.appName = config.appName();
		this.inetHost = config.inetHost();
		this.inetPort = config.inetPort();
		this.pid = (String)props.get(Constants.SERVICE_PID);

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
		final Channel priorChannel = channels.put(channelId, ch);
		if (priorChannel != null) {
			System.err.println(String.format("Prior channel %s existed in initializer %s", priorChannel, this));
		}

		executorService.execute(() -> {
			String configurationPid;
			try {
				configurationPid = configUtil.createDynamicChannelHandlerConfig(channelId, config.appName(),
						config.inetHost(), config.inetPort(), Arrays.asList(config.factoryPids()),
						Arrays.asList(config.handlerNames()), extraProperties);
				if (configurationPids.contains(configurationPid)) {
					System.err.println(String.format("%s received a previously created configuration pid: %s", ChannelInitializerProvider.this, configurationPid));
				}
				configurationPids.add(configurationPid);
				configurations.put(channelId, configurationPid);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});

		System.out.println(String.format("%s initializing channel %s:[%s] with event loop %s", this, ch, channelId, ch.eventLoop()));

	}

	@Override
	public String toString() {
		return String.format("%s:%s:%s:%d", pid, appName, inetHost, inetPort);
	}

}
