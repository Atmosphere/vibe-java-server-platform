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
package org.atmosphere.vibe.platform.server.atmosphere2;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereResourceEvent;
import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter;
import org.atmosphere.cpr.AtmosphereResourceImpl;
import org.atmosphere.cpr.AtmosphereResponse;
import org.atmosphere.vibe.platform.Actions;
import org.atmosphere.vibe.platform.Data;
import org.atmosphere.vibe.platform.HttpStatus;
import org.atmosphere.vibe.platform.server.AbstractServerHttpExchange;
import org.atmosphere.vibe.platform.server.ServerHttpExchange;

/**
 * {@link ServerHttpExchange} for Atmosphere 2.
 *
 * @author Donghwan Kim
 */
public class AtmosphereServerHttpExchange extends AbstractServerHttpExchange {

    private final AtmosphereResource resource;
    private final AtmosphereResponse response;
    private final AtmosphereRequest request;

    public AtmosphereServerHttpExchange(AtmosphereResource resource) {
        this.resource = resource.suspend();
        // Prevent IllegalStateException when the connection gets closed.
        this.response = AtmosphereResourceImpl.class.cast(resource).getResponse(false);
        this.request = AtmosphereResourceImpl.class.cast(resource).getRequest(false);
        resource.addEventListener(new AtmosphereResourceEventListenerAdapter() {
            @Override
            public void onDisconnect(AtmosphereResourceEvent event) {
                closeActions.fire();
            }

            @Override
            public void onClose(AtmosphereResourceEvent event) {
                closeActions.fire();
            }

            @Override
            public void onThrowable(AtmosphereResourceEvent event) {
                errorActions.fire(event.throwable());
            }
        });
    }

    @Override
    public String uri() {
        String uri = request.getRequestURI();
        if (request.getQueryString() != null) {
            uri += "?" + request.getQueryString();
        }
        return uri;
    }

    @Override
    public String method() {
        return request.getMethod();
    }

    @Override
    public Set<String> headerNames() {
        Set<String> headerNames = new LinkedHashSet<>();
        Enumeration<String> enumeration = request.getHeaderNames();
        while (enumeration.hasMoreElements()) {
            headerNames.add(enumeration.nextElement());
        }
        return headerNames;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> headers(String name) {
        return Collections.list(request.getHeaders(name));
    }

    @Override
    protected void readBody() {
        try {
            ServletInputStream input = request.getInputStream();
            // HTTP 1.1 says that the default charset is ISO-8859-1
            // http://www.w3.org/International/O-HTTP-charset#charset
            String charsetName = request.getCharacterEncoding();
            final Charset charset = Charset.forName(charsetName == null ? "ISO-8859-1" : charsetName);
            if (request.getServletContext().getMinorVersion() > 0) {
                // 3.1+ asynchronous
                new AsyncBodyReader(input, charset, bodyActions, errorActions);
            } else {
                // 3.0 synchronous
                new SyncBodyReader(input, charset, bodyActions, errorActions);
            }
        } catch (IOException e) {
            errorActions.fire(e);
        }
    }

    private abstract static class BodyReader {
        final ServletInputStream input;
        final Charset charset;
        final Actions<Data> bodyActions;
        final Actions<Throwable> errorActions;
        final StringBuilder body = new StringBuilder();

        public BodyReader(ServletInputStream input, Charset charset, Actions<Data> bodyActions, Actions<Throwable> errorActions) {
            this.input = input;
            this.charset = charset;
            this.bodyActions = bodyActions;
            this.errorActions = errorActions;
            start();
        }

        abstract void start();

        void read() throws IOException {
            int bytesRead = -1;
            byte buffer[] = new byte[8192];
            while (ready() && (bytesRead = input.read(buffer)) != -1) {
                String data = new String(buffer, 0, bytesRead, charset);
                body.append(data);
            }
        }

        abstract boolean ready();

        void end() {
            bodyActions.fire(new Data(body.toString()));
        }
    }

    private static class AsyncBodyReader extends BodyReader {
        public AsyncBodyReader(ServletInputStream input, Charset charset, Actions<Data> bodyActions, Actions<Throwable> errorActions) {
            super(input, charset, bodyActions, errorActions);
        }

        @Override
        void start() {
            input.setReadListener(new ReadListener() {
                @Override
                public void onDataAvailable() throws IOException {
                    read();
                }

                @Override
                public void onAllDataRead() throws IOException {
                    end();
                }

                @Override
                public void onError(Throwable t) {
                    errorActions.fire(t);
                }
            });
        }

        @Override
        boolean ready() {
            return input.isReady();
        }
    }

    private static class SyncBodyReader extends BodyReader {
        public SyncBodyReader(ServletInputStream input, Charset charset, Actions<Data> bodyActions, Actions<Throwable> errorActions) {
            super(input, charset, bodyActions, errorActions);
        }

        @Override
        void start() {
            try {
                read();
                end();
            } catch (IOException e) {
                errorActions.fire(e);
            }
        }

        @Override
        boolean ready() {
            return true;
        }
    }

    @Override
    protected void doSetHeader(String name, String value) {
        response.setHeader(name, value);
    }

    @Override
    protected void doWrite(ByteBuffer byteBuffer) {
        try {
            byte[] bytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(bytes);
            OutputStream outputStream = response.getOutputStream();
            outputStream.write(bytes);
            outputStream.flush();
        } catch (IOException e) {
            errorActions.fire(e);
        }
    }

    @Override
    protected void doSetStatus(HttpStatus status) {
        response.setStatus(status.code());
    }

    @Override
    protected void doWrite(String data) {
        try {
            PrintWriter writer = response.getWriter();
            writer.print(data);
            writer.flush();
        } catch (IOException e) {
            errorActions.fire(e);
        }
    }

    @Override
    protected void doEnd() {
        resource.resume();
    }

    /**
     * {@link AtmosphereResource} is available.
     */
    @Override
    public <T> T unwrap(Class<T> clazz) {
        return AtmosphereResource.class.isAssignableFrom(clazz) ? clazz.cast(resource) : null;
    }

}
