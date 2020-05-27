package com.kopemorta.socksashttp.core;

import com.kopemorta.socksashttp.entities.SocksProxy;
import lombok.AllArgsConstructor;

import java.nio.channels.SocketChannel;

@AllArgsConstructor
public class SocksAsHttpFactory {

    private final BootstrapFactory bootstrapFactory;


    public SocketChannel createSocksAsHttpChannel(final SocksProxy socksProxy) {
        return null;
    }
}
