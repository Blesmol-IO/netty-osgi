
# Example application

A GoGo shell should appear. Running `lb` and `list` should look similar to
below. Note how all the components except for the dynamic handler are in
an `active` state. The handler is still `satisfied` since it hasn't been
used yet.

```
____________________________
Welcome to Apache Felix Gogo

g! Feb 10, 2018 1:33:25 PM io.netty.util.internal.PlatformDependent <clinit>
INFO: Your platform does not provide complete low-level API for accessing direct buffers reliably. Unless explicitly requested, heap buffer will always be preferred to avoid potential system instability.
lb
START LEVEL 1
   ID|State      |Level|Name
    0|Active     |    0|System Bundle (5.6.10)|5.6.10
    1|Active     |    1|io.blesmol.netty.example (0.0.0.201802102119)|0.0.0.201802102119
    2|Active     |    1|io.blesmol.netty.provider (0.1.0.201802102133)|0.1.0.201802102133
    3|Active     |    1|Netty/Buffer (4.1.20.Final)|4.1.20.Final
    4|Active     |    1|Netty/Common (4.1.20.Final)|4.1.20.Final
    5|Active     |    1|Netty/Resolver (4.1.20.Final)|4.1.20.Final
    6|Active     |    1|Netty/Transport (4.1.20.Final)|4.1.20.Final
    7|Active     |    1|Apache Felix Configuration Admin Service (1.8.16)|1.8.16
    8|Active     |    1|Apache Felix Gogo Command (1.0.2)|1.0.2
    9|Active     |    1|Apache Felix Gogo Runtime (1.0.10)|1.0.10
   10|Active     |    1|Apache Felix Gogo Shell (1.0.0)|1.0.0
   11|Active     |    1|Apache Felix Declarative Services (2.0.14)|2.0.14
g! list
 BundleId Component Name Default State
    Component Id State      PIDs (Factory PID)
 [   1]   io.blesmol.netty.example.Application  enabled
    [   0] [active      ] 
 [   2]   io.blesmol.netty.provider.BootstrapProvider  enabled
    [   1] [satisfied   ] 
 [   2]   io.blesmol.netty.provider.ConfigurationUtilProvider  enabled
    [   2] [active      ] 
 [   2]   io.blesmol.netty.provider.NettyClientProvider  enabled
 [   2]   io.blesmol.netty.provider.NettyServerProvider  enabled
    [   3] [active      ] io.blesmol.netty.api.NettyServer.ab1b1339-368b-481b-b3e3-9b08b52412f1 (io.blesmol.netty.api.NettyServer )
 [   2]   io.blesmol.netty.provider.NioEventLoopGroupProvider  enabled
    [   4] [active      ] 
 [   2]   io.blesmol.netty.provider.OsgiChannelHandlerProvider  enabled
    [   5] [satisfied   ] io.blesmol.netty.api.OsgiChannelHandler.049555a3-8e03-47e7-9bb4-4970989bd1cf (io.blesmol.netty.api.OsgiChannelHandler )
 [   2]   io.blesmol.netty.provider.ServerBootstrapProvider  enabled
    [   6] [active      ] 
 [   2]   io.blesmol.netty.provider.SocketChannelInitializerProvider  enabled
    [   7] [active      ] io.netty.channel.ChannelInitializer.6aa9ad02-153c-43c4-bea7-d5a15fa3445c (io.netty.channel.ChannelInitializer )
g! 
```

