package io.blesmol.netty.provider;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.osgi.service.component.annotations.ServiceScope;

import io.blesmol.netty.api.Configuration;
import io.blesmol.netty.api.OsgiChannelHandler;
import io.blesmol.netty.util.ChannelHandlerConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

@Component(
	configurationPid= Configuration.OSGI_CHANNEL_HANDLER_PID,
	configurationPolicy= ConfigurationPolicy.REQUIRE,
	scope=ServiceScope.PROTOTYPE,
	immediate=true
)
public class OsgiChannelHandlerProvider implements OsgiChannelHandler {

	private ChannelHandlerContext ctx;
	private final ConcurrentMap<ChannelHandlerConfig, ChannelHandler> channelHandlers = new ConcurrentHashMap<>();

	void close() {
		if (ctx != null) {
			channelHandlers.values().forEach(
				h ->
				removeHandlerFromPipeline(ctx.pipeline(), h)
			);
		}
		this.ctx = null;
	}

	@Activate
	void activate() { }

	@Deactivate
	void deactivate() {
		close();
	}

	/*
	 * Set a new channel handler service, if it uses the same app name as this
	 * 
	 *  Potential race condition:
	 *    This method is called via OSGi, adding a handler to a map
	 *    Right then, handlerAdded is called via Netty, which reads the map
	 *    Now, both methods try to add the handler to the pipeline
	 *  
	 *  Accept the race condition and catch the exception
	 *  in addHandlerToPipeline.
	 */
	@Reference(
		policy=ReferencePolicy.DYNAMIC,
		cardinality=ReferenceCardinality.OPTIONAL,
		name="channelHandler"
	)
	void setChannelHandler(ChannelHandler handler, Map<String, Object> props) {

		final ChannelHandlerConfig config = ChannelHandlerConfig.fromMap(props);

		// TODO debug log the addition
		channelHandlers.put(config, handler);
		if (ctx != null) {
			addHandlerToPipeline(ctx.pipeline(), config, handler);
		}

	}

	void unsetChannelHandler(ChannelHandler handler, Map<String, Object> props) {

		final ChannelHandlerConfig config = ChannelHandlerConfig.fromMap(props);

		// TODO debug log the removal
		channelHandlers.remove(config);
		if (ctx != null) {
			removeHandlerFromPipeline(ctx.pipeline(), handler);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * Adds whatever channel handlers existed at time of being added
	 *  
	 * @see io.netty.channel.ChannelHandler#handlerAdded(io.netty.channel.ChannelHandlerContext)
	 */
	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		this.ctx = ctx;
		// TODO debug log the add?
		channelHandlers.entrySet().forEach(e ->
			addHandlerToPipeline(ctx.pipeline(), e.getKey(), e.getValue())			
		);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.netty.channel.ChannelHandler#handlerRemoved(io.netty.channel.ChannelHandlerContext)
	 */
	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		close();
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		cause.printStackTrace();
		throw new Exception(cause);
	}

	boolean addHandlerToPipeline(ChannelPipeline pipeline, ChannelHandlerConfig config, ChannelHandler handler) {
		boolean result = true;
		try {
			if (!ChannelHandlerConfig.EMPTY.equals(config.getBefore())) {
				pipeline.addBefore(config.getBefore(), config.getHandleName(), handler);
			} else if (!ChannelHandlerConfig.EMPTY.equals(config.getAfter())) {
				pipeline.addAfter(config.getAfter(), config.getHandleName(), handler);
			} else if (config.getFirst()) {
				pipeline.addFirst(config.getHandleName(), handler);
			} else { // default 
				pipeline.addLast(config.getHandleName(), handler);
			}
		} catch (IllegalArgumentException | NullPointerException e) {
			// TODO log
			// Thrown if the pipeline already contains the handler
			// indicating this thread lost the race, or handler is now null
			result = false;
		}
		
		return result;

	}

	boolean removeHandlerFromPipeline(ChannelPipeline pipeline, ChannelHandler handler) {
		boolean result = true;
		try {
			pipeline.remove(handler);
		} catch (NoSuchElementException | NullPointerException e) {
			// TODO log
			// Thrown if the handler has already been removed or is null
			result = false;
		}

		return result;
	}
}
