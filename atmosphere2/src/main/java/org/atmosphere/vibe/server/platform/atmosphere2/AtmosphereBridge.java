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
package org.atmosphere.vibe.server.platform.atmosphere2;

import java.io.IOException;
import java.util.UUID;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResource.TRANSPORT;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.handler.AtmosphereHandlerAdapter;
import org.atmosphere.vibe.server.platform.Action;
import org.atmosphere.vibe.server.platform.Actions;
import org.atmosphere.vibe.server.platform.ServerHttpExchange;
import org.atmosphere.vibe.server.platform.ServerWebSocket;
import org.atmosphere.vibe.server.platform.SimpleActions;

/**
 * Convenient class to install Atmosphere bridge.
 *
 * @author Donghwan Kim
 */
public class AtmosphereBridge {

    private Actions<ServerHttpExchange> httpActions = new SimpleActions<>();
    private Actions<ServerWebSocket> wsActions = new SimpleActions<>();

    public AtmosphereBridge(ServletContext context, String path) {
        AtmosphereServlet servlet = null;
        try {
            servlet = context.createServlet(AtmosphereServlet.class);
        } catch (ServletException e) {
            throw new RuntimeException(e);
        }
        AtmosphereFramework framework = servlet.framework();
        framework.addAtmosphereHandler("/", new AtmosphereHandlerAdapter() {
            @Override
            public void onRequest(AtmosphereResource resource) throws IOException {
                if (resource.transport() == TRANSPORT.WEBSOCKET) {
                    if (resource.getRequest().getMethod().equals("GET")) {
                        wsActions.fire(new AtmosphereServerWebSocket(resource));
                    }
                } else {
                    httpActions.fire(new AtmosphereServerHttpExchange(resource));
                }
            }
        });

        ServletRegistration.Dynamic reg = context.addServlet("vibe#" + UUID.randomUUID(), servlet);
        reg.setAsyncSupported(true);
        reg.addMapping(path);
        reg.setInitParameter(ApplicationConfig.DISABLE_ATMOSPHEREINTERCEPTOR, Boolean.TRUE.toString());
    }

    /**
     * Adds an action to be called on HTTP request with
     * {@link ServerHttpExchange}.
     */
    public AtmosphereBridge httpAction(Action<ServerHttpExchange> action) {
        httpActions.add(action);
        return this;
    }

    /**
     * Adds an action to be called on WebSocket connection with
     * {@link ServerWebSocket} in open state.
     */
    public AtmosphereBridge websocketAction(Action<ServerWebSocket> action) {
        wsActions.add(action);
        return this;
    }

}
