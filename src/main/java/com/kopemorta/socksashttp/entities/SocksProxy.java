package com.kopemorta.socksashttp.entities;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.net.SocketAddress;

@EqualsAndHashCode
public abstract class SocksProxy {

    @Getter
    private final SocketAddress proxyAdr;

    SocksProxy(SocketAddress proxyAdr) {
        this.proxyAdr = proxyAdr;
    }
}
