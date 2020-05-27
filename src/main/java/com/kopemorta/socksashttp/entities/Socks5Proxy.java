package com.kopemorta.socksashttp.entities;

import java.net.SocketAddress;

public final class Socks5Proxy extends SocksProxy {
    public Socks5Proxy(SocketAddress proxyAdr) {
        super(proxyAdr);
    }
}
