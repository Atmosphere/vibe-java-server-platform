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
package org.atmosphere.vibe.platform.bridge.vertx2;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

import org.atmosphere.vibe.platform.action.Action;
import org.atmosphere.vibe.platform.http.ServerHttpExchange;
import org.atmosphere.vibe.platform.test.ServerHttpExchangeTest;
import org.junit.Test;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;

public class VertxServerHttpExchangeTest extends ServerHttpExchangeTest {

    HttpServer server;

    @Override
    protected void startServer() {
        server = VertxFactory.newVertx().createHttpServer();
        RouteMatcher matcher = new RouteMatcher();
        matcher.all("/test", new VibeRequestHandler().onhttp(performer.serverAction()));
        server.requestHandler(matcher);
        server.listen(port);
    }

    @Override
    protected void stopServer() {
        server.close();
    }

    @Test
    public void unwrap() {
        performer.onserver(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                assertThat(http.unwrap(HttpServerRequest.class), instanceOf(HttpServerRequest.class));
                performer.start();
            }
        })
        .send();
    }

}
