# Blesmol Netty

From [ChannelHandlerContext](https://netty.io/4.1/api/io/netty/channel/ChannelHandlerContext.html):

> A non-trivial application could insert, remove, or replace handlers in the pipeline dynamically at runtime.

This project is an OSGi implementation of [Netty](http://netty.io/), exposing multiple ways to dynamically
configure a Netty server bootstrap instance and dynamic add or remove channel handlers from the pipelines created by the server.

## Install

Refer to the Releases page for the latest zipped p2 repository.

## Example

Refer to the [example application component](io.blesmol.netty.example/src/io/blesmol/netty/example/Application.java). Note its usage of `immediate=true`. Without this, the component may stay in a `REGISTERED` OSGi state instead of `ACTIVE`. Instead, immediately activate it.

## API

### `EventExecutorGroupHandler`

Specifies a handler wants to use some other `EventExecutorGroup` instead of the default channel event loop. The dynamic handler will obtain the event executor group via this interface and pass it when adding the handler to the pipeline.

## Use

* Deploy the API, provider, and util bundles
* Create an application via `io.blesmol.netty.api.ConfigurationUtil`
* Provide 1 to many `ChannelHandler`s components:
  * (Recommended) And configure them via Configuration Admin, using the component property type `io.blesmol.netty.api.Configuration.ChannelHandler` as a guide.
  * Or, set service properties on the components using `io.blesmol.netty.api.Property.ChannelHandler` as a guide.
  * Using either approach, optionally specify the handler's ordering (last is default).

## References

* [Netty User Guide for 4.x](http://netty.io/wiki/user-guide-for-4.x.html)

* [Bndtools Tutorial](http://bndtools.org/tutorial.html)

* [Getting Started with OSGi Declarative Services](http://blog.vogella.com/2016/06/21/getting-started-with-osgi-declarative-services/)

* [What's New in Declarative Services 1.3?](http://njbartlett.name/2015/08/17/osgir6-declarative-services.html)

* [Service Components](http://enroute.osgi.org/doc/217-ds.html)

* [Control OSGi DS Component Instances](http://blog.vogella.com/2017/02/13/control-osgi-ds-component-instances/)

* [Control OSGi DS Component Instances via Configuration Admin](http://blog.vogella.com/2017/02/24/control-osgi-ds-component-instances-via-configuration-admin/)

## Todos

* All the `TODO` comments
* Units tests
* Javadocs
* Maybe refactor the channel initializer to be a prototype. Then it shouldn't
  need to be configured. Or keep it as is, dunno.
* Stress test case that adds 100s of embedded channels, each w/ scores of handlers
* Measure test case times and store as performance measures

## Gotchas

### `ChannelHandler.handlerAdded` Implementations

If a channel handler is dynamically created (via the dynamic handler) and removes itself in `handlerAdded`, the dynamic handler will fail on adding the next handler. This is because it conceptually treats the list as a linked list, using the previous handler as a key when adding the next handler. When the handler is deleted, this key becomes invalid, causing an exception:

```java
java.util.NoSuchElementException: httpConnectProxyServer
	at io.netty.channel.DefaultChannelPipeline.getContextOrDie(DefaultChannelPipeline.java:1097)
	at io.netty.channel.DefaultChannelPipeline.addAfter(DefaultChannelPipeline.java:320)
	at io.blesmol.netty.provider.DynamicChannelHandlerProvider.lambda$12(DynamicChannelHandlerProvider.java:439)
```

Workarounds:

* If the pattern is to add a bunch of handlers and then remove the current handler, then instead use the dynamic handler and configure its list of handlers directly.
* In the next handler remove the previous handler.
* Schedule the handler to be removed on its executor. Note: don't just `executor.execute` it, since that could be run in the current thread at the discretion of the executor.
* Do not remove handlers specified via the dynamic handler
* Option D...


## License

Apache 2.0