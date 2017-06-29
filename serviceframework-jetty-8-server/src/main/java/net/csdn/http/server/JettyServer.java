package net.csdn.http.server;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.SessionManager;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import java.io.IOException;

/**
 * Created by allwefantasy on 29/6/2017.
 */
public class JettyServer {
    public Server createServer(String host, int port,
                               int minThreads, int maxThreads,
                               String staticDir,
                               String templateDir,
                               boolean staticEnable,
                               boolean classPathEnable, boolean sessionEanble, AbstractHandler abstractHandler) {
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setDaemon(true);
        threadPool.setMaxThreads(minThreads);
        threadPool.setMinThreads(maxThreads);

        SelectChannelConnector connector = new SelectChannelConnector();
        connector.setHost(host);
        connector.setPort(port);

        Server server = new Server();
        server.setThreadPool(threadPool);
        server.addConnector(connector);

        HandlerList handlers = new HandlerList();

        if (staticEnable) {
            ResourceHandler resource_handler = new ResourceHandler();
            resource_handler.setDirectoriesListed(false);
            try {
                if (classPathEnable) {
                    String webDir = this.getClass().getClassLoader().getResource(staticDir).toExternalForm();
                    resource_handler.setBaseResource(
                            Resource.newResource(webDir));
                } else {
                    resource_handler.setBaseResource(
                            Resource.newResource(templateDir + "/" + staticDir + "/"));
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
            if (sessionEanble) {
                SessionManager sessionManager = new HashSessionManager();
                sessionManager.setSessionIdPathParameterName("none");
                handlers.setHandlers(new Handler[]{resource_handler, new SessionHandler(sessionManager), abstractHandler});
            } else {
                handlers.setHandlers(new Handler[]{resource_handler, abstractHandler});
            }

        } else {
            handlers.setHandlers(new Handler[]{abstractHandler});
        }


        server.setHandler(handlers);
        return server;
    }
}
