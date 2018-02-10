
# Example application

A GoGo shell should appear. Running `lb` and `list` should look similar to
below. Note how all the components except for the dynamic handler are in
an `active` state. The handler is still `satisfied` since it hasn't been
used yet.

```
____________________________
Welcome to Apache Felix Gogo

g! Feb 09, 2018 5:20:18 PM io.netty.util.internal.PlatformDependent <clinit>
INFO: Your platform does not provide complete low-level API for accessing direct buffers reliably. Unless explicitly requested, heap buffer will always be preferred to avoid potential system instability.
lb
START LEVEL 1
   ID|State      |Level|Name
    0|Active     |    0|System Bundle (5.6.10)|5.6.10
    1|Active     |    1|io.blesmol.netty.example (0.0.0.201802091925)|0.0.0.201802091925
    2|Active     |    1|io.blesmol.netty.provider (0.1.0.201802100119)|0.1.0.201802100119
    3|Active     |    1|io.blesmol.netty.util (0.1.0.201802091925)|0.1.0.201802091925
    4|Active     |    1|Netty/Buffer (4.1.20.Final)|4.1.20.Final
    5|Active     |    1|Netty/Common (4.1.20.Final)|4.1.20.Final
    6|Active     |    1|Netty/Resolver (4.1.20.Final)|4.1.20.Final
    7|Active     |    1|Netty/Transport (4.1.20.Final)|4.1.20.Final
    8|Active     |    1|Apache Felix Configuration Admin Service (1.8.16)|1.8.16
    9|Active     |    1|Apache Felix Gogo Command (1.0.2)|1.0.2
   10|Active     |    1|Apache Felix Gogo Runtime (1.0.10)|1.0.10
   11|Active     |    1|Apache Felix Gogo Shell (1.0.0)|1.0.0
   12|Active     |    1|Apache Felix Declarative Services (2.0.14)|2.0.14
g! list
 BundleId Component Name Default State
    Component Id State      PIDs (Factory PID)
 [   1]   io.blesmol.netty.example.Application  enabled
    [   0] [active      ] 
 [   2]   io.blesmol.netty.provider.ConfigurationUtilProvider  enabled
    [   1] [active      ] 
 [   2]   io.blesmol.netty.provider.NettyServerProvider  enabled
    [   2] [active      ] io.blesmol.netty.api.NettyServer.fd5255e5-820d-43b3-bfa9-6f9eddcfe1ba (io.blesmol.netty.api.NettyServer )
 [   2]   io.blesmol.netty.provider.NioEventLoopGroupProvider  enabled
    [   3] [active      ] 
 [   2]   io.blesmol.netty.provider.OsgiChannelHandlerProvider  enabled
    [   4] [satisfied   ] io.blesmol.netty.api.OsgiChannelHandler.328735a1-7b54-4109-9485-12daf4192e5b (io.blesmol.netty.api.OsgiChannelHandler )
 [   2]   io.blesmol.netty.provider.ServerBootstrapProvider  enabled
    [   5] [active      ] 
 [   2]   io.blesmol.netty.provider.SocketChannelInitializerProvider  enabled
    [   6] [active      ] io.netty.channel.ChannelInitializer.1f0a5fc7-d900-413b-bab4-851b19b1cc59 (io.netty.channel.ChannelInitializer )
g! 
```

