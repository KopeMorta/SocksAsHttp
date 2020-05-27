package com.kopemorta.socksashttp;

import com.kopemorta.socksashttp.entities.SocksProxy;
import io.netty.channel.ServerChannel;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SocksStore {

    private final Map<SocksProxy, Optional<ServerChannel>> proxyChannelMap;

    public SocksStore() {
        proxyChannelMap = new ConcurrentHashMap<>();
    }


    /**
     * Add new proxy only if old value not present
     * @param socksProxy proxy to add
     */
    public void addProxy(final SocksProxy socksProxy) {
//        proxyChannelMap.putIfAbsent();
    }
}
