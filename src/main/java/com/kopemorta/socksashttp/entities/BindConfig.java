package com.kopemorta.socksashttp.entities;

import lombok.Value;

import java.net.InetAddress;

@Value
public class BindConfig {

    public static final BindConfig DEFAULT_CONFIG = of(InetAddress.getLoopbackAddress(), 32768, 60999);


    InetAddress bindAdr;
    PortRange portRange;


    public static BindConfig of(final InetAddress bindAdr, final int startPort, final int endPort) {
        return new BindConfig(bindAdr, new PortRange(startPort, endPort));
    }

    @Value
    public static class PortRange {
        int startPort;
        int endPort;
    }
}
