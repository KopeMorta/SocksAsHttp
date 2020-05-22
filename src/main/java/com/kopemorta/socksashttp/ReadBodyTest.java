package com.kopemorta.socksashttp;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.HttpHeaders;

import java.nio.charset.StandardCharsets;

public class ReadBodyTest extends ChannelDuplexHandler {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        System.out.println("Зашли сюда");
//        if(msg instanceof DefaultLastHttpContent httpContent) {
//            HttpHeaders entries = httpContent.trailingHeaders();
//
//            final ByteBuf buf = httpContent.content();
//            byte[] bytes = new byte[buf.readableBytes()];
//            buf.readBytes(bytes);
////
//            System.out.println(new String(bytes, StandardCharsets.UTF_8));
//        }
//        super.channelRead(ctx, msg);
    }
}
