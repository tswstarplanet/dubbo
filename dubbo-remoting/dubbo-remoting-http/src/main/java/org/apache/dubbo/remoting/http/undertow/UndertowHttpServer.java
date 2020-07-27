/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.remoting.http.undertow;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.remoting.Constants;
import org.apache.dubbo.remoting.http.HttpHandler;
import org.apache.dubbo.remoting.http.servlet.DispatcherServlet;
import org.apache.dubbo.remoting.http.support.AbstractHttpServer;

import javax.servlet.ServletException;

import java.util.Optional;

import static org.apache.dubbo.common.constants.CommonConstants.IO_THREADS_KEY;
import static org.apache.dubbo.common.constants.CommonConstants.WORK_THREADS_KEY;

public class UndertowHttpServer extends AbstractHttpServer {
    private static final Logger logger = LoggerFactory.getLogger(UndertowHttpServer.class);

    private Undertow server;

    public UndertowHttpServer(URL url, final HttpHandler handler) {
        super(url, handler);
        DispatcherServlet.addHttpHandler(url.getParameter(Constants.BIND_PORT_KEY, url.getPort()), handler);
        DeploymentInfo servletBuilder = Servlets.deployment()
                .setClassLoader(Thread.currentThread().getContextClassLoader())
                .setContextPath("/")
                .setDeploymentName("dubbo-undertow.war")
                .addServlets(
                        Servlets.servlet("dispatcher", DispatcherServlet.class)
                                .addMapping("/*"));

        DeploymentManager manager = Servlets.defaultContainer().addDeployment(servletBuilder);
        manager.deploy();
        PathHandler path;
        try {
            path = Handlers.path()
                    .addPrefixPath("/", manager.start());
        } catch (ServletException e) {
            throw new IllegalStateException("Undertow server start fails");
        }

        Undertow.Builder builder = Undertow.builder()
                .addHttpListener(url.getParameter(Constants.BIND_PORT_KEY, url.getPort())
                        , url.getParameter(Constants.BIND_IP_KEY, url.getHost()))
                .setHandler(path);
        Optional.ofNullable(url.getParameter(IO_THREADS_KEY, Integer.class)).ifPresent(iothreads -> builder.setIoThreads(iothreads));
        Optional.ofNullable(url.getParameter(WORK_THREADS_KEY, Integer.class)).ifPresent(workthreads -> builder.setWorkerThreads(workthreads));
        server = builder.build();
        server.start();
    }

    @Override
    public void close() {
        super.close();
        try {
            if (server != null) {
                server.stop();
            }
        } catch (Exception e) {
            logger.error("Error when close undertow server", e);
        }
    }
}
