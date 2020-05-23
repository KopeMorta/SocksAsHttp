package com.kopemorta.socksashttp.httpproxy;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.Promise;

import java.net.SocketAddress;
import java.util.Optional;

@ChannelHandler.Sharable
public class HttpServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private static final HttpResponseStatus CONNECTION_ESTABLISHED_RESPONSE_STATUS =
            new HttpResponseStatus(200, "Connection Established");


    private final int maxContentLength;

    public HttpServerHandler(int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }


    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest msg) {
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
            if (socketAddress.isPresent()) {
                hostSA = socketAddress.get();
            }
            // if parse result is empty - close send response and connection
            else {
                ctx.channel().writeAndFlush(
                        new DefaultHttpResponse(msg.protocolVersion(), HttpResponseStatus.BAD_GATEWAY));

                ProxyServerUtils.closeOnFlush(ctx.channel());
                return;
            }
        }

        // create https tunnel
        //noinspection IfStatementWithIdenticalBranches
        if (ssl) {
            final Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(Https.createHttpsPromiseListener(ctx, msg.protocolVersion()));

            final Channel inboundChannel = ctx.channel();
            final Bootstrap b = new Bootstrap()
                    .group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new DirectClientHandler(promise));

            b.connect(hostSA).addListener(Https.createHttpsConnectListener(ctx, msg.protocolVersion()));
        }
        // default http proxy
        else {
            final Promise<Channel> promise = ctx.executor().newPromise();
            promise.addListener(Http.createHttpPromiseListener(ctx, msg.protocolVersion()));

            final Channel inboundChannel = ctx.channel();
            final Bootstrap b = new Bootstrap()
                    .group(inboundChannel.eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new DirectClientHandler(promise));


            // increase RefCnt to can read content in listener
            msg.retain();

            b.connect(hostSA).addListener(Http.createHttpConnectListener(ctx, msg, maxContentLength));
        }
    }


    private static class Https {
        private static FutureListener<Channel> createHttpsPromiseListener(final ChannelHandlerContext ctx,
                                                                          final HttpVersion requestVersion) {
            return future -> {
                final Channel outboundChannel = future.getNow();
                ctx.pipeline().addLast(new HttpResponseEncoder()); // add response encoder for send response

                /* if connection established send Connection Established response,
                 * remove all redundant handlers and add RelayHandler
                 */
                if (future.isSuccess()) {
                    // send response
                    final ChannelFuture responseFuture = ctx.channel().writeAndFlush(
                            new DefaultHttpResponse(requestVersion, CONNECTION_ESTABLISHED_RESPONSE_STATUS));


                    responseFuture.addListener((ChannelFutureListener) channelFuture -> {
                        final ChannelPipeline thisPipe = ctx.pipeline();
                        // remove not using handlers
                        thisPipe.remove(HttpServerHandler.class);
                        thisPipe.remove(HttpRequestDecoder.class);
                        thisPipe.remove(HttpResponseEncoder.class);

                        // add relay handler to inbound and outbound channels
                        outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                        thisPipe.addLast(new RelayHandler(outboundChannel));
                    });
                }
                // connection failed - send fail response and close connection
                else {
                    ctx.channel().writeAndFlush(
                            new DefaultHttpResponse(requestVersion, HttpResponseStatus.BAD_GATEWAY));

                    ProxyServerUtils.closeOnFlush(ctx.channel());
                }
            };
        }

        private static ChannelFutureListener createHttpsConnectListener(final ChannelHandlerContext ctx,
                                                                        final HttpVersion requestVersion) {
            return future -> {
                //noinspection StatementWithEmptyBody
                if (future.isSuccess()) { // connection established
                    // now not need, for future
                }
                // connection failed - send fail response and close connection
                else {
                    ctx.pipeline().addLast(new HttpResponseEncoder()); // add encoder for send response

                    // Send to inbound channel fail response and close channel
                    final Throwable cause = future.cause();
                    if (cause instanceof ConnectTimeoutException) {
                        ctx.channel().writeAndFlush(
                                new DefaultHttpResponse(requestVersion, HttpResponseStatus.GATEWAY_TIMEOUT));
                    } else {
                        ctx.channel().writeAndFlush(
                                new DefaultHttpResponse(requestVersion, HttpResponseStatus.BAD_GATEWAY));
                    }

                    ProxyServerUtils.closeOnFlush(ctx.channel());
                }
            };
        }
    }

    private static class Http {
        private static FutureListener<Channel> createHttpPromiseListener(final ChannelHandlerContext ctx,
                                                                         final HttpVersion requestVersion) {
            return future -> {
                final Channel outboundChannel = future.getNow();

                /* if connection established - remove all redundant handlers
                 * and add RelayHandler
                 */
                if (future.isSuccess()) {
                    final ChannelPipeline thisPipe = ctx.pipeline();
                    thisPipe.remove(HttpServerHandler.class);
                    thisPipe.remove(HttpRequestDecoder.class);

                    outboundChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                    thisPipe.addLast(new RelayHandler(outboundChannel));
                } else {
                    ctx.pipeline().addLast(new HttpResponseEncoder()); // add encoder for send response
                    ctx.channel().writeAndFlush(
                            new DefaultHttpResponse(requestVersion, HttpResponseStatus.BAD_GATEWAY));

                    ProxyServerUtils.closeOnFlush(ctx.channel());
                }
            };
        }

        private static ChannelFutureListener createHttpConnectListener(final ChannelHandlerContext ctx,
                                                                       final FullHttpRequest msg,
                                                                       final int maxContentLength) {
            return future -> {
                // if connection successful need send to him full copy of input http request
                if (future.isSuccess()) {
                    final Channel channel = future.channel(); // out channel

                    // add request encoder to channel and HttpObjectAggregator for can send FullHttpRequest
                    channel.pipeline().addLast(new HttpRequestEncoder())
                            .addLast(new HttpObjectAggregator(maxContentLength));

                    // if have content - send DefaultFullHttpRequest, else DefaultHttpRequest
                    if (msg.content().capacity() > 0) {
                        channel.writeAndFlush(
                                new DefaultFullHttpRequest(
                                        msg.protocolVersion(),
                                        msg.method(),
                                        msg.uri(),
                                        msg.content(),
                                        msg.headers(),
                                        msg.trailingHeaders()
                                )
                        );
                    } else {
                        channel.writeAndFlush(new DefaultHttpRequest(msg.protocolVersion(), msg.method(), msg.uri(), msg.headers()));
                    }

                    // remove redundant handlers
                    channel.pipeline().remove(HttpRequestEncoder.class);
                    channel.pipeline().remove(HttpObjectAggregator.class);
                } else {
                    ctx.pipeline()
                            .addLast(new HttpResponseEncoder()); // add encoder for send response

                    // Send to inbound channel fail response and close channel
                    final Throwable cause = future.cause();
                    if (cause instanceof ConnectTimeoutException) {
                        ctx.channel().writeAndFlush(
                                new DefaultHttpResponse(msg.protocolVersion(), HttpResponseStatus.GATEWAY_TIMEOUT));
                    } else {
                        ctx.channel().writeAndFlush(
                                new DefaultHttpResponse(msg.protocolVersion(), HttpResponseStatus.BAD_GATEWAY));
                    }

                    ProxyServerUtils.closeOnFlush(ctx.channel());
                }

                // decrease RefCnt after read
                msg.content().release();
            };
        }
    }
}
