# Blesmol Netty

From [ChannelHandlerContext](https://netty.io/4.1/api/io/netty/channel/ChannelHandlerContext.html):

> A non-trivial application could insert, remove, or replace handlers in the pipeline dynamically at runtime.

This project is an OSGi implementation of [Netty](http://netty.io/), exposing multiple ways to dynamically
configure a Netty server bootstrap instance and dynamic add or remove channel handlers from the pipelines created by the server.

## Install

Refer to the [Releases](releases) page for the latest zipped p2 repository.

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
* Refactor the code to remove the utils project
* Maybe refactor the channel initializer to be a prototype. Then it shouldn't
  need to be configured. Or keep it as is, dunno.
* Stress test case that adds 100s of embedded channels, each w/ scores of handlers
* Measure test case times and store as performance measures

## License

Apache 2.0