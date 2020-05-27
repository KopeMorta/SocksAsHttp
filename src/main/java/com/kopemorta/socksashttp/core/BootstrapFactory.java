package com.kopemorta.socksashttp.core;

import com.kopemorta.socksashttp.Config;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;

import java.io.Closeable;

public class BootstrapFactory implements Closeable {

    private static final boolean EPOLL_AVAILABLE = Epoll.isAvailable();


    private final EventLoopGroup bossGroup;
    private final EventLoopGroup workerGroup;

    public BootstrapFactory(final Config config) {
        if(EPOLL_AVAILABLE) {
            this.bossGroup = new EpollEventLoopGroup(config.getBossThreads());
            this.workerGroup = new EpollEventLoopGroup(config.getWorkerThreads());
        } else {
            this.bossGroup = new NioEventLoopGroup(config.getBossThreads());
            this.workerGroup = new NioEventLoopGroup(config.getWorkerThreads());
        }
    }


    public ServerBootstrap createServerBootstrap() {
        return new ServerBootstrap()
                .group(this.bossGroup, this.workerGroup)
                .channel(EPOLL_AVAILABLE ? EpollServerSocketChannel.class : NioServerSocketChannel.class)
                .childOption(ChannelOption.SO_KEEPALIVE, true);
    }

    public Bootstrap createClientBootstrap() {
        return new Bootstrap()
                .group(this.workerGroup)
                .channel(EPOLL_AVAILABLE ? EpollSocketChannel.class : NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .option(ChannelOption.SO_KEEPALIVE, true);
    }


    @Override
    public void close() {
        this.bossGroup.shutdownGracefully();
        this.workerGroup.shutdownGracefully();
    }
}
