package net.csdn.modules.http;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.ApplicationContext;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.annotation.NoTransaction;
import net.csdn.common.collect.Tuple;
import net.csdn.common.env.Environment;
import net.csdn.common.exception.ExceptionHandler;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.JdbcEngine;
import net.csdn.common.settings.Settings;
import net.csdn.constants.CError;
import net.csdn.http.server.JettyServer;
import net.csdn.jpa.JPA;
import net.csdn.modules.controller.API;
import net.csdn.modules.http.processor.HttpFinishProcessor;
import net.csdn.modules.http.processor.HttpStartProcessor;
import net.csdn.modules.http.processor.ProcessInfo;
import net.csdn.modules.http.processor.impl.DefaultHttpFinishProcessor;
import net.csdn.modules.http.processor.impl.DefaultHttpStartProcessor;
import net.csdn.modules.http.support.HttpHolder;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;


/**
 * BlogInfo: william
 * Date: 11-9-2
 * Time: 下午1:29
 */
@Singleton
public class HttpServer {
    private final Server server;
    private CSLogger logger = Loggers.getLogger(getClass());

    private RestController restController;
    private boolean disableMysql = false;
    private Settings settings;
    private API api;
    private final int httpPort;
    private int boundPort = -1;
    private final ApplicationContext applicationContext;

    private List<HttpStartProcessor> httpStartProcessorList = new ArrayList();
    private List<HttpFinishProcessor> httpFinishProcessorList = new ArrayList();

    private static ThreadLocal<HttpHolder> httpHolder = new ThreadLocal<HttpHolder>();

    public static void setHttpHolder(HttpHolder value) {
        httpHolder.set(value);
    }

    public static void removeHttpHolder() {
        httpHolder.remove();
    }

    public static HttpHolder httpHolder() {
        return httpHolder.get();
    }


    @Inject
    public HttpServer(Settings settings, RestController restController, API api) {
        this.settings = settings;
        this.restController = restController;
        this.api = api;
        this.applicationContext = ApplicationContext.currentOrNull();
        registerHttpStartProcessor(new DefaultHttpStartProcessor());
        registerHttpFinishProcessor(new DefaultHttpFinishProcessor());

        Environment environment = new Environment(settings);
        disableMysql = JdbcEngine.primaryDisabled(settings, ServiceFramwork.currentMode().name());
        JettyServer jettyServer = new JettyServer();
        httpPort = settings.getAsInt("http.port", generateHttpPort());

        server = jettyServer.createServer(settings.getAsInt("http.threads.min", 100),
                settings.getAsInt("http.threads.max", 1000));
        String httpHost = settings.get("http.host", "");
        if (httpHost != null && httpHost.trim().length() == 0) {
            httpHost = null;
        }
        ServerConnector connector = jettyServer.createConnector(server, httpHost, httpPort);
        connector.setIdleTimeout(settings.getAsInt("http.server.idleTimeout", 30000));

        server.addConnector(connector);


        jettyServer.connfigureServer(server,
                settings.get("serviceframework.static.loader.classpath.dir", "assets"),
                environment.templateDirFile().getPath(),
                settings.getAsBoolean("application.static.enable", false), settings.getAsBoolean("serviceframework.static.loader.classpath.enable", false),
                settings.getAsBoolean("application.session.enable", false), new DefaultHandler()

        );
    }

    public void registerHttpStartProcessor(HttpStartProcessor httpStartProcessor) {
        httpStartProcessorList.add(httpStartProcessor);
    }

    public void registerHttpFinishProcessor(HttpFinishProcessor httpFinishProcessor) {
        httpFinishProcessorList.add(httpFinishProcessor);
    }


    private int generateHttpPort() {
        if (settings.getAsInt("http.port", -1) == 0) {
            return 0;
        }
        String clzz = settings.get("http.class.port", "");
        if (!clzz.isEmpty()) {
            PortGenerator pg = null;
            try {
                pg = (PortGenerator) (Class.forName(clzz).newInstance());
                return pg.getPort();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return 8080;
    }

    public int getHttpPort() {
        return boundPort > 0 ? boundPort : httpPort;
    }

    public boolean isRunning() {
        return server.isStarted();
    }

    class DefaultHandler extends AbstractHandler {


        private void rollback(Method action) {
            if (!disableMysql && action != null && action.getAnnotation(NoTransaction.class) == null) {
                try {
                    JPA.getJPAConfig().getJPAContext().closeTx(true);
                } catch (Exception e2) {
                    //ignore
                }
            }
        }

        private void defaultErrorAction(DefaultResponse channel, Exception e) {
            if (restController.errorHandlerKey() != null) {
                ApplicationController errorApplicationController = ServiceFramwork.currentInjector().getInstance(restController.errorHandlerKey().v1());
                try {
                    RestController.enhanceApplicationController(errorApplicationController, HttpServer.httpHolder().restRequest(), channel);
                    try {
                        restController.errorHandlerKey().v2().invoke(errorApplicationController, e);
                    } catch (Exception e2) {
                        ExceptionHandler.renderHandle(e2);
                        channel.send();
                    }
                } catch (Exception e1) {
                    logger.error(CError.SystemProcessingError, e1);
                }
            } else {
                try {
                    channel.error(e);
                } catch (IOException e1) {
                    logger.error(CError.SystemProcessingError, e1);
                }
            }
        }


        @Override
        public void handle(String s, Request request, final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) throws IOException, ServletException {
            EnhancementContext.Scope scope = applicationContext == null ? null : applicationContext.activate();
            try {
            DefaultResponse channel = new DefaultResponse(httpServletRequest, httpServletResponse, restController);
            ProcessInfo processInfo = new ProcessInfo();
            try {

                RestRequest restRequest = new DefaultRestRequest(httpServletRequest);
                HttpServer.setHttpHolder(new HttpHolder(restRequest, channel));
                Tuple<Class<ApplicationController>, Method> tuple = restController.getHandler(restRequest);
                if (tuple != null) {
                    processInfo.method = tuple.v2();
                }
                for (HttpStartProcessor httpStartProcessor : httpStartProcessorList) {
                    httpStartProcessor.process(settings, httpServletRequest, httpServletResponse, processInfo);
                }
                try {
                    restController.dispatchRequest(restRequest, channel);
                } catch (Exception e) {
                    ExceptionHandler.renderHandle(e);
                }
                channel.send();
            } catch (Exception e) {
                if (!"qps-overflow".equals(e.getMessage())) {
                    logger.error(CError.SystemProcessingError, e);
                }
                //回滚
                rollback(processInfo.method);
                //如果有默认的action处理异常统一展示结果的话
                defaultErrorAction(channel, e);
            } finally {
                processInfo.status = channel.status();
                if (channel.content() != null) {
                    processInfo.responseLength = channel.content().length();
                }
                for (HttpFinishProcessor httpFinishProcessor : httpFinishProcessorList) {
                    httpFinishProcessor.process(settings, httpServletRequest, httpServletResponse, processInfo);
                }
                HttpServer.removeHttpHolder();
            }


            } finally {
                if (scope != null) {
                    scope.close();
                }
            }
        }
    }


    public void start() {
        try {
            if (!server.isStarted()) {
                server.start();
            }
            Connector[] connectors = server.getConnectors();
            for (int i = 0; i < connectors.length; i++) {
                if (connectors[i] instanceof ServerConnector) {
                    int local = ((ServerConnector) connectors[i]).getLocalPort();
                    if (local > 0) {
                        boundPort = local;
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("HTTP server did not bind", e);
        }
    }

    public void close() {
        try {
            if (server.isRunning() || server.isStarted() || server.isStarting() || server.isStopping()) {
                server.stop();
            }
        } catch (Exception e) {
            throw new IllegalStateException("HTTP server did not stop", e);
        } finally {
            try {
                server.destroy();
            } catch (Exception ignored) {
                // stop already reported the primary failure when it threw
            }
        }
    }

    public void join() {
        try {
            server.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


}
