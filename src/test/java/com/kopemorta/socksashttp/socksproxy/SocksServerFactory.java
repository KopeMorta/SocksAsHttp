/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.kopemorta.socksashttp.socksproxy;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;

import java.net.SocketAddress;

public final class SocksServerFactory {

    private final ServerBootstrap serverBootstrap;

    public SocksServerFactory() {
        final EventLoopGroup bossGroup = new NioEventLoopGroup(2);
        final EventLoopGroup workerGroup = new NioEventLoopGroup(4);

        this.serverBootstrap = new ServerBootstrap()
                .group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new SocksServerInitializer());
    }


    public SocketAddress createSocksProxy() {
        Channel channel = serverBootstrap.bind(0).syncUninterruptibly().channel();

        return channel.localAddress();
    }
}