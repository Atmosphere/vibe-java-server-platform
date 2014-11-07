/*
 * Copyright 2014 The Vibe Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atmosphere.vibe.platform.server.netty4;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpServerCodec;

import java.net.URI;

import org.atmosphere.vibe.platform.Action;
import org.atmosphere.vibe.platform.server.ServerHttpExchange;
import org.atmosphere.vibe.platform.test.server.ServerHttpExchangeTestTemplate;
import org.junit.Test;

public class NettyServerHttpExchangeTest extends ServerHttpExchangeTestTemplate {

    EventLoopGroup bossGroup;
    EventLoopGroup workerGroup;

    @Override
    protected void startServer() {
        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
        .channel(NioServerSocketChannel.class)
        .childHandler(new ChannelInitializer<SocketChannel>() {
            @Override
            public void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();
                pipeline.addLast(new HttpServerCodec())
                .addLast(new VibeServerCodec() {
                    @Override
                    protected boolean accept(HttpRequest req) {
                        return URI.create(req.getUri()).getPath().equals("/test");
                    }
                    
                    @Override
                    protected Action<ServerHttpExchange> httpAction() {
                        return new Action<ServerHttpExchange>() {
                            @Override
                            public void on(ServerHttpExchange http) {
                                performer.serverAction().on(http);
                            }
                        };
                    }
                });
            }
        });
        bootstrap.bind(port);
    }

    @Override
    protected void stopServer() {
        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
    }

    @Test
    public void unwrap() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                assertThat(http.unwrap(ChannelHandlerContext.class), instanceOf(ChannelHandlerContext.class));
                assertThat(http.unwrap(HttpRequest.class), instanceOf(HttpRequest.class));
                assertThat(http.unwrap(HttpResponse.class), instanceOf(HttpResponse.class));
                performer.start();
            }
        })
        .send();
    }

}