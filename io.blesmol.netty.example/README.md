
# Example application

A GoGo shell should appear. Running `lb` and `list` should look similar to below

```
____________________________
Welcome to Apache Felix Gogo

g! lb
START LEVEL 1
   ID|State      |Level|Name
    0|Active     |    0|System Bundle (5.6.10)|5.6.10
    1|Active     |    1|io.blesmol.netty.example (0.0.0.201802091837)|0.0.0.201802091837
    2|Active     |    1|io.blesmol.netty.provider (0.1.0.201802091916)|0.1.0.201802091916
    3|Active     |    1|io.blesmol.netty.util (0.1.0.201802091831)|0.1.0.201802091831
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
    [   2] [satisfied   ] io.blesmol.netty.api.NettyServer.f306c9b0-3834-4ba2-9d9b-9142270a6870 (io.blesmol.netty.api.NettyServer )
 [   2]   io.blesmol.netty.provider.NioEventLoopGroupProvider  enabled
    [   3] [satisfied   ] 
 [   2]   io.blesmol.netty.provider.OsgiChannelHandlerProvider  enabled
    [   4] [satisfied   ] io.blesmol.netty.api.OsgiChannelHandler.9f54c0a7-d0ff-45a9-a19f-484cd1f1e45c (io.blesmol.netty.api.OsgiChannelHandler )
 [   2]   io.blesmol.netty.provider.ServerBootstrapProvider  enabled
    [   5] [satisfied   ] 
 [   2]   io.blesmol.netty.provider.SocketChannelInitializerProvider  enabled
    [   6] [satisfied   ] io.netty.channel.ChannelInitializer.b3e7c174-56e3-46fd-93ce-2154dc91e5af (io.netty.channel.ChannelInitializer )
g! 
```