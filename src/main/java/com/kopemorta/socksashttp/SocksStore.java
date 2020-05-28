package com.kopemorta.socksashttp;

import com.kopemorta.socksashttp.core.BindController;
import com.kopemorta.socksashttp.core.BootstrapFactory;
import com.kopemorta.socksashttp.entities.SocksProxy;
import com.kopemorta.socksashttp.exceptions.CannotBindException;
import com.kopemorta.socksashttp.httpproxy.HttpServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpRequestDecoder;
import lombok.AllArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class SocksStore {

    private static final Logger LOGGER = LogManager.getLogger();

    private final Map<SocksProxy, Channel> proxyChannelMap;

    private final Config config;
    private final BindController bindController;
    private final BootstrapFactory bootstrapFactory;


    public SocksStore(final Config config) {
        this.config = config;
        this.bindController = new BindController(config.getBindConfigList());
        this.bootstrapFactory = new BootstrapFactory(config);

        this.proxyChannelMap = new ConcurrentHashMap<>();
    }


    public Optional<SocketAddress> addProxy(final SocksProxy socksProxy) {
        final Channel resultChannel = proxyChannelMap.computeIfAbsent(socksProxy, (proxy -> {
            final ServerBootstrap serverBootstrap = bootstrapFactory.createServerBootstrap()
                    .childHandler(new SocksAsHttpChannelInitializer(config.getMaxHttpContentLength(), proxy, bootstrapFactory));

            try {
                return bindController.bindBootstrap(serverBootstrap);
            } catch (CannotBindException e) {
                LOGGER.error("Cannot bind channel to address", e);
            }

            return null;
        }));

        if (resultChannel == null)
            return Optional.empty();

        return Optional.of(resultChannel.localAddress());
    }

    public void releaseProxy(final SocksProxy socksProxy) {
        final Channel channel = proxyChannelMap.get(socksProxy);
        if (channel == null)
            return;

        channel.close().addListener(getChannelCloseListener(channel.localAddress()));
    }

    public void releaseAll() {
        // copy original map and clear it
        final Map<SocksProxy, Channel> copy = new HashMap<>(proxyChannelMap);
        this.proxyChannelMap.clear();

        // call close channel and add closeFuture listener which try release binded address
        copy.values()
                .forEach(channel ->
                        channel.close().addListener(getChannelCloseListener(channel.localAddress()))
                );
    }

    private ChannelFutureListener getChannelCloseListener(final SocketAddress socketAddress) {
        return future -> bindController.releaseBindAdr(socketAddress);
    }


    @AllArgsConstructor
    private static class SocksAsHttpChannelInitializer extends ChannelInitializer<Channel> {
        private final int maxHttpContentLength;
        private final SocksProxy proxy;
        private final BootstrapFactory bootstrapFactory;

        @Override
        protected void initChannel(Channel ch) {
            ch.pipeline().addLast(new HttpRequestDecoder())
                    .addLast(new HttpObjectAggregator(maxHttpContentLength))
                    .addLast(new HttpServerHandler(bootstrapFactory, proxy, maxHttpContentLength));
        }
    }
}
