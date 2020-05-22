package com.kopemorta.socksashttp;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;

public class HttpRequestHandler extends SimpleChannelInboundHandler<HttpRequest> {
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) {

        HttpHeaders headers = msg.headers();

        System.out.println(headers.contains("Host"));
        System.out.println(headers.contains("host"));
        System.out.println(msg);
        System.out.println();

        ctx.pipeline().addLast(new ReadBodyTest());
    }
}
