package com.kopemorta.socksashttp.entities;

import java.net.SocketAddress;

public final class Socks4Proxy extends SocksProxy {
    public Socks4Proxy(SocketAddress proxyAdr) {
        super(proxyAdr);
    }
}
