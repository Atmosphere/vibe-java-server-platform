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
package org.atmosphere.vibe.platform.server.servlet3;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.atmosphere.vibe.platform.Action;
import org.atmosphere.vibe.platform.server.ServerHttpExchange;
import org.atmosphere.vibe.platform.test.server.ServerHttpExchangeTestTemplate;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.junit.Ignore;
import org.junit.Test;

public class ServletServerHttpExchangeTest extends ServerHttpExchangeTestTemplate {

    Server server;

    @Override
    protected void startServer() throws Exception {
        server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);

        // ServletContext
        ServletContextHandler handler = new ServletContextHandler();
        server.setHandler(handler);
        ServletContextListener listener = new ServletContextListener() {
            @Override
            public void contextInitialized(ServletContextEvent event) {
                ServletContext context = event.getServletContext();
                @SuppressWarnings("serial")
                ServletRegistration.Dynamic reg = context.addServlet(VibeServlet.class.getName(), new VibeServlet() {
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
                reg.setAsyncSupported(true);
                reg.addMapping("/test");
            }

            @Override
            public void contextDestroyed(ServletContextEvent sce) {
            }
        };
        handler.addEventListener(listener);

        server.start();
    }

    @Override
    protected void stopServer() throws Exception {
        server.stop();
    }

    @Test
    public void unwrap() {
        performer.serverAction(new Action<ServerHttpExchange>() {
            @Override
            public void on(ServerHttpExchange http) {
                assertThat(http.unwrap(HttpServletRequest.class), instanceOf(HttpServletRequest.class));
                assertThat(http.unwrap(HttpServletResponse.class), instanceOf(HttpServletResponse.class));
                performer.start();
            }
        })
        .send();
    }

    @Override
    @Test
    @Ignore
    public void closeAction_abnormal() {}

}
