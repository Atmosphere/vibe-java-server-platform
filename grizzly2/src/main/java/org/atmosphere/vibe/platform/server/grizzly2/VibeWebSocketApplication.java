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
package org.atmosphere.vibe.platform.server.grizzly2;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.atmosphere.vibe.platform.Action;
import org.atmosphere.vibe.platform.server.ServerWebSocket;
import org.glassfish.grizzly.websockets.DataFrame;
import org.glassfish.grizzly.websockets.DefaultWebSocket;
import org.glassfish.grizzly.websockets.WebSocket;
import org.glassfish.grizzly.websockets.WebSocketApplication;

/**
 * WebSocketApplication to process {@link WebSocket} into
 * {@link GrizzlyServerWebSocket}.
 * <p>
 * 
 * <pre>
 * NetworkListener listener = httpServer.getListener("grizzly");
 * listener.registerAddOn(new WebSocketAddOn());
 * WebSocketEngine.getEngine().register("", "/vibe", new VibeWebSocketApplication() {
 *     {@literal @}Override
 *     protected Action&ltServerWebSocket&gt wsAction() {
 *         return server.wsAction();
 *     }
 * });
 * </pre>
 *
 * @author Donghwan Kim
 */
public abstract class VibeWebSocketApplication extends WebSocketApplication {
    
    // WebSocketApplication is already using WebSocket as a key of ConcurrentHashMap
    // From https://github.com/GrizzlyNIO/grizzly-mirror/blob/2_3_17/modules/websockets/src/main/java/org/glassfish/grizzly/websockets/WebSocketApplication.java#L63
    private Map<WebSocket, GrizzlyServerWebSocket> sockets = new ConcurrentHashMap<>();

    @Override
    public void onConnect(WebSocket socket) {
        super.onConnect(socket);
        GrizzlyServerWebSocket ws = new GrizzlyServerWebSocket((DefaultWebSocket) socket);
        sockets.put(socket, ws);
        wsAction().on(ws);
    }

    /**
     * An {@link Action} to consume {@link ServerWebSocket}.
     */
    protected abstract Action<ServerWebSocket> wsAction();
    
    @Override
    public void onClose(WebSocket socket, DataFrame frame) {
        super.onClose(socket, frame);
        sockets.remove(socket);
    }

    @Override
    protected boolean onError(WebSocket webSocket, Throwable t) {
        boolean ret = super.onError(webSocket, t);
        sockets.get(webSocket).onError(t);
        return ret;
    }

}