package com.kopemorta.socksashttp;

import com.kopemorta.socksashttp.entities.Socks4Proxy;
import com.kopemorta.socksashttp.entities.Socks5Proxy;
import com.kopemorta.socksashttp.entities.SocksProxy;
import com.kopemorta.socksashttp.socksproxy.SocksServerFactory;
import kong.unirest.HttpResponse;
import kong.unirest.UnirestInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SocksStoreTest {

    private static final SocksServerFactory SOCKS_SERVER_FACTORY = new SocksServerFactory();
    private static final List<SocksProxy> SOCKS_PROXIES = new ArrayList<>();
    
    @BeforeAll
    public static void createSocksProxies() {
        for (int i = 0; i < 500; i++) {
            final SocketAddress unificatedSocksProxy = SOCKS_SERVER_FACTORY.createSocksProxy();

            final SocksProxy socksProxy;
            if(((int)(Math.random() * 2)) > 1)
                socksProxy = new Socks5Proxy(unificatedSocksProxy);
            else
                socksProxy = new Socks4Proxy(unificatedSocksProxy);

            SOCKS_PROXIES.add(socksProxy);
        }
    }


    private final SocksStore socksStore = new SocksStore(Config.builder().build());
    private final Map<SocksProxy, SocketAddress> socksProxyHttpProxyMap = new HashMap<>();

    @BeforeEach
    public void createSocksAsHttpProxies() {
        final int testProxyCount = SOCKS_PROXIES.size() / 10;
        for (int i = 0; i < testProxyCount; i++) {
            final SocksProxy rndSocksProxy = SOCKS_PROXIES.get((int) (Math.random() * SOCKS_PROXIES.size()));

            final Optional<SocketAddress> httpProxyAdr = socksStore.addProxy(rndSocksProxy);
            if(!httpProxyAdr.isPresent())
                 fail("Cannot create http proxy from socks proxy.");
            else
                socksProxyHttpProxyMap.put(rndSocksProxy, httpProxyAdr.get());
        }
    }

    @AfterEach
    public void clearOldData() {
        socksStore.close();
        socksProxyHttpProxyMap.clear();
    }


    @Test
    public void goodWorkTest() {
        final Optional<SocketAddress> any = socksProxyHttpProxyMap.values().stream().findAny();
        if(!any.isPresent())
            fail("socksProxyHttpProxyMap is empty");

        final InetSocketAddress socketAddress = (InetSocketAddress) any.get();
        final kong.unirest.Config unirestConfig = new kong.unirest.Config()
                .proxy(socketAddress.getAddress().getHostAddress(), socketAddress.getPort());

        final UnirestInstance unirestInstance = new UnirestInstance(unirestConfig);
        final HttpResponse httpResponse = unirestInstance.get("https://postman-echo.com/get").asEmpty();


        assertEquals(httpResponse.getStatus(), 200, "Bad response status code");
    }
}
