package com.kopemorta.socksashttp.httpproxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@ChannelHandler.Sharable
public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final HttpVersion PROXY_HTTP_VERSION = HttpVersion.HTTP_1_1;
    private static final HttpResponseStatus CONNECTION_ESTABLISHED_RESPONSE_STATUS =
            new HttpResponseStatus(200, "Connection Established");


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        final HttpMethod method = msg.method();
        final HttpHeaders headers = msg.headers();
        final String uri = msg.uri();
        final boolean ssl = method.equals(HttpMethod.CONNECT);


        SocketAddress hostSA; // destination host
        // if have header HOST - parse this to SocketAddress
        if (headers.contains("host")) {
            final String host = headers.get("host");
            hostSA = HostUtil.parseHost(host, ssl);
        }
        // if header HOST not preset - try parse host from uri
        else {
            final Optional<SocketAddress> socketAddress = HostUtil.parseUri(uri, ssl);
            if (socketAddress.isPresent())
                hostSA = socketAddress.get();
            else {
                ctx.channel().writeAndFlush(
                        new DefaultHttpResponse(PROXY_HTTP_VERSION, HttpResponseStatus.BAD_GATEWAY));

                ProxyServerUtils.closeOnFlush(ctx.channel());
                return;
            }
        }

        if (method.equals(HttpMethod.CONNECT)) { // create https tunnel
            final Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(
                    (FutureListener<Channel>) future -> {
                        final Channel outboundChannel = future.getNow();
                        ctx.pipeline().addLast(new HttpResponseEncoder()); // add encoder for send response

                        /* if all ok send Connection Established response,
                         * remove all redundant handlers
                         * and add RelayHandler
                         */
                        if (future.isSuccess()) {
                            ChannelFuture responseFuture = ctx.channel().writeAndFlush(
                                    new DefaultHttpResponse(PROXY_HTTP_VERSION, CONNECTION_ESTABLISHED_RESPONSE_STATUS));

                            responseFuture.addListener((ChannelFutureListener) channelFuture -> {
                                final ChannelPipeline p = ctx.pipeline();
                                p.remove(HttpServerHandler.this);
                                p.remove(HttpRequestDecoder.class);
                                p.remove(HttpResponseEncoder.class);

                                outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                                ctx.pipeline().addLast(new RelayHandler(outboundChannel));
                            });
                        } else {
                            ctx.channel().writeAndFlush(
                                    new DefaultHttpResponse(PROXY_HTTP_VERSION, HttpResponseStatus.BAD_GATEWAY));

                            ProxyServerUtils.closeOnFlush(ctx.channel());
                        }
                    });

            final Channel inboundChannel = ctx.channel();
            final Bootstrap b = new Bootstrap()
                    .group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new DirectClientHandler(promise));

            b.connect(hostSA).addListener((ChannelFutureListener) future -> {
                //noinspection StatementWithEmptyBody
                if (future.isSuccess()) {
                    // Connection established use handler provided results
                } else {
                    ctx.pipeline().addLast(new HttpResponseEncoder()); // add encoder for send response
                    // Close the connection if the connection attempt has failed.
                    final Throwable cause = future.cause();
                    if (cause instanceof ConnectTimeoutException) {
                        ctx.channel().writeAndFlush(
                                new DefaultHttpResponse(PROXY_HTTP_VERSION, HttpResponseStatus.GATEWAY_TIMEOUT));
                    } else {
                        ctx.channel().writeAndFlush(
                                new DefaultHttpResponse(PROXY_HTTP_VERSION, HttpResponseStatus.BAD_GATEWAY));
                    }

                    ProxyServerUtils.closeOnFlush(ctx.channel());
                }
            });
        } else {
            // TODO: default http

            final Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(
                    (FutureListener<Channel>) future -> {
                        final Channel outboundChannel = future.getNow();

                        /* if all ok - send input body to out channel
                         * remove all redundant handlers
                         * and add RelayHandler
                         */
                        if (future.isSuccess()) {
                            final ChannelPipeline thisPipe = ctx.pipeline();
                            thisPipe.remove(HttpServerHandler.this);
                            thisPipe.remove(HttpRequestDecoder.class);

//                            thisPipe.addLast(new BodyToOutChannel(outboundChannel));
                            outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                            thisPipe.addLast(new RelayHandler(outboundChannel));
                        } else {
                            ctx.pipeline().addLast(new HttpResponseEncoder()); // add encoder for send response
                            ctx.channel().writeAndFlush(
                                    new DefaultHttpResponse(PROXY_HTTP_VERSION, HttpResponseStatus.BAD_GATEWAY));

                            ProxyServerUtils.closeOnFlush(ctx.channel());
                        }
                    });

            final Channel inboundChannel = ctx.channel();
            final Bootstrap b = new Bootstrap()
                    .group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new DirectClientHandler(promise));



            final ByteBuf content = msg.retain().content();

            b.connect(hostSA).addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    final Channel channel = future.channel();
                    channel.pipeline().addLast(new HttpRequestEncoder(), new HttpObjectAggregator(1024 * 1024));
                    if(content.capacity() > 0) {
                        channel.writeAndFlush(
                                new DefaultFullHttpRequest(
                                        msg.protocolVersion(),
                                        msg.method(),
                                        msg.uri(),
                                        content,
                                        msg.headers(),
                                        msg.trailingHeaders()
                                )
                        );
                    } else {
                        channel.writeAndFlush(new DefaultHttpRequest(msg.protocolVersion(), msg.method(), msg.uri(), msg.headers()));
                    }

                    channel.pipeline().remove(HttpRequestEncoder.class);
                    channel.pipeline().remove(HttpObjectAggregator.class);

                    // Connection established use handler provided results
                } else {
                    ctx.pipeline().addLast(new HttpResponseEncoder()); // add encoder for send response
                    // Close the connection if the connection attempt has failed.
                    final Throwable cause = future.cause();
                    if (cause instanceof ConnectTimeoutException) {
                        ctx.channel().writeAndFlush(
                                new DefaultHttpResponse(PROXY_HTTP_VERSION, HttpResponseStatus.GATEWAY_TIMEOUT));
                    } else {
                        ctx.channel().writeAndFlush(
                                new DefaultHttpResponse(PROXY_HTTP_VERSION, HttpResponseStatus.BAD_GATEWAY));
                    }

                    ProxyServerUtils.closeOnFlush(ctx.channel());
                }

                content.release();
            });

        }
    }
}
