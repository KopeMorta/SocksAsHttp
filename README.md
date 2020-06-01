# SocksAsHttp
This tool need to using socks(4/5) proxy as http proxy.
The motivation for creating this tool was that many http clients in java do not support socks proxy support.
And as I see it, no one is going to fix it. 
For example java.net.http.httpclient still not support socks proxy - https://bugs.openjdk.java.net/browse/JDK-8214516


This tool built on Netty and uses its handlers therefore, it should be fast and should not load resources.
So far this is the first version and additional settings may be required, but it seemed to me that in this state it copes well.


In current moment this tool have only Java code interface.

# Usage
First need build [Config](/src/main/java/com/kopemorta/socksashttp/Config.java)
In the vast majority of cases, the default config will do, but in some cases, you will need to fine tune it.
All configuration fields are trivial, except for one, which is described below.
### [BindConfig](/src/main/java/com/kopemorta/socksashttp/entities/BindConfig.java)
The key feature of this tool from the rest is that it supports many SocksAsHttp in one process. 
And since a dedicated port is required for each instance, they may not be enough. 
Well, since most likely you will need to use this utility locally - just select a few local IPs to supplement the missing number of ports.

Therefore, for special cases there is this setting.

It’s easy to use:
```java
final BindConfig bindConfig = BindConfig.of(InetAddress.getByName("127.0.0.2"), 10000, 60000);

final Config config = Config.builder()
	.bindConfigList(Collections.singletonList(bindConfig))
	.build();
```
This will create 50k ports.

### Using [SocksStore](/src/main/java/com/kopemorta/socksashttp/SocksStore.java)
Now can create SocksStore with with the config that was made above.
This object have 3 public methods:

#### Optional<SocketAddress> addProxy(final SocksProxy socksProxy)
This method try create SocksAsHttp. 
Accepts [SocksProxy](/src/main/java/com/kopemorta/socksashttp/entities/SocksProxy.java).
And return empty optional only if no have free ports with write log, or SocketAddress with http-proxy interface.

#### void releaseProxy(final SocksProxy socksProxy)
This method tries to release SocksProxy, for this it tries to find its channel and release it.

#### void releaseAll()
All the same as releaseProxy but for all.
