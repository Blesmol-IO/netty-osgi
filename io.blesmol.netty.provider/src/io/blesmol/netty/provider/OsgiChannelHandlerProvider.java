package io.blesmol.netty.provider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ServiceScope;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.OsgiChannelHandler;
import io.blesmol.netty.api.Property;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@Component(configurationPid = Configuration.OSGI_CHANNEL_HANDLER_PID, configurationPolicy = ConfigurationPolicy.REQUIRE, scope = ServiceScope.PROTOTYPE)
public class OsgiChannelHandlerProvider extends ChannelInboundHandlerAdapter implements OsgiChannelHandler {

	// can be set by different threads
	private volatile ChannelHandlerContext ctx;
	private final ConcurrentMap<String, ChannelHandler> referencedHandlers = new ConcurrentHashMap<>();
	private final List<String> handlerConfig = new CopyOnWriteArrayList<>();

	// Reentrant guards
	private AtomicBoolean added = new AtomicBoolean(false);

	@Activate
	void activate(Configuration.OsgiChannelHandler config, Map<String, ?> properties) {
		handlerConfig.addAll(Arrays.asList(config.handlers()));
	}

	// OSGi Cmpn 112.5.12 Modification states the modified method is called and then
	// any bound services are removed. So we have to filter which handlers are added
	// here instead of relying on the existing handlers in the reference handler
	// map.
	// FIXME: run below on a background thread to free OSGi-called method
	@Modified
	void modified(Configuration.OsgiChannelHandler config, Map<String, ?> properties) {
		List<String> previousConfig = new ArrayList<>(handlerConfig);
		handlerConfig.clear();
		handlerConfig.addAll(Arrays.asList(config.handlers()));

		// Only call below if handler already added.
		if (this.added.get()) {

			Set<String> previous = new HashSet<>(previousConfig);
			Set<String> current = new HashSet<>(handlerConfig);
			Set<String> all = new HashSet<>(previous);
			all.addAll(current);

			Set<String> toRemove = new HashSet<>(all);
			toRemove.removeAll(current);

			Set<String> toAdd = new HashSet<>(all);
			toAdd.removeAll(previous);

			// FIXME: Race condition on ctx below; could be made null in another thread.
			// Remove all that need to be removed.
			if (ctx != null) {
				toRemove.forEach(h -> {
					ctx.pipeline().remove(h);
				});
			}

			// FIXME: another race condition, like above.
			// Using the current ordered configuration, add only those needing to be added,
			// maintaining order.
			if (ctx != null) {
				IntStream.range(0, config.handlers().length).filter(i -> toAdd.contains(config.handlers()[i]))
						.forEach(i -> {
							// If this is the first handler, add as first
							if (i == 0) {
								ctx.pipeline().addFirst(referencedHandlers.get(config.handlers()[i]));
							}
							// Otherwise, use the previous handler as a reference and add after it
							else {
								ctx.pipeline().addAfter(config.handlers()[i - i], config.handlers()[i],
										referencedHandlers.get(config.handlers()[i]));
							}
						});
			}

		}

	}

	@Deactivate
	void deactivate(Configuration.OsgiChannelHandler config, Map<String, ?> properties) {
		// TODO: do anything?
		// Called when configuration is removed, bundle is going away, channel is closed
		// (via channel init)
	}

	@Reference(policy = ReferencePolicy.DYNAMIC, cardinality = ReferenceCardinality.OPTIONAL, name = "channelHandler")
	void setChannelHandler(ChannelHandler handler, Map<String, Object> props) {
		try {
			referencedHandlers.put((String) props.get(Property.ChannelHandler.HANDLE_NAME), handler);
		} catch (NullPointerException e) {
			System.err.println(String.format("Error: the handler '%s' does not have a string property named '%s'",
					handler.toString(), Property.ChannelHandler.HANDLE_NAME));
			throw new IllegalStateException(e);
		}
	}

	void unsetChannelHandler(ChannelHandler handler, Map<String, Object> props) {
		try {
			referencedHandlers.remove((String) props.get(Property.ChannelHandler.HANDLE_NAME), handler);
		} catch (NullPointerException e) {
			System.err.println(String.format("Error: the handler '%s' does not have a string property named '%s'",
					handler.toString(), Property.ChannelHandler.HANDLE_NAME));
			throw new IllegalStateException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * Adds whatever channel handlers existed at time of being added.
	 * 
	 * @see io.netty.channel.ChannelHandler#handlerAdded(io.netty.channel.
	 * ChannelHandlerContext)
	 */
	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		// Add all existing handlers
		// should only be called once regardless; guard against that here
		if (added.compareAndSet(false, true)) {
			this.ctx = ctx;
			handlerConfig.forEach(k -> {
				ctx.pipeline().addLast(referencedHandlers.get(k));
			});

		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		ctx.close();
	}

}
