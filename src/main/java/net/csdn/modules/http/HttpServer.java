package net.csdn.modules.http;

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import net.csdn.ServiceFramwork;
import net.csdn.common.env.Environment;
import net.csdn.common.exception.ArgumentErrorException;
import net.csdn.common.exception.ExceptionHandler;
import net.csdn.common.exception.RecordExistedException;
import net.csdn.common.exception.RecordNotFoundException;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.constants.CError;
import net.csdn.hibernate.support.filter.CSDNStatFilterstat;
import net.csdn.jpa.JPA;
import net.csdn.modules.http.support.HttpHolder;
import net.csdn.modules.http.support.HttpStatus;
import net.csdn.modules.log.SystemLogger;
import net.sf.json.JSONException;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static net.csdn.common.collections.WowCollections.isNull;


/**
 * BlogInfo: william
 * Date: 11-9-2
 * Time: 下午1:29
 */
public class HttpServer {
    private final Server server;
    private CSLogger logger = Loggers.getLogger(getClass());

    private RestController restController;
    private boolean disableMysql = false;
    private Settings settings;
    private SystemLogger systemLogger;

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
    public HttpServer(Settings settings, SystemLogger systemLogger, RestController restController) {
        this.settings = settings;
        this.systemLogger = systemLogger;
        this.restController = restController;
        Environment environment = new Environment(settings);
        disableMysql = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mysql.disable", false);
        server = new Server();
        SelectChannelConnector connector = new SelectChannelConnector();
        QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMinThreads(settings.getAsInt("http.threads.min", 100));
        threadPool.setMaxThreads(settings.getAsInt("http.threads.max", 1000));
        connector.setThreadPool(threadPool);
        connector.setPort(settings.getAsInt("http.port", 8080));
        server.addConnector(connector);

        HandlerList handlers = new HandlerList();
        if (settings.getAsBoolean("application.static.enable", false)) {
            ResourceHandler resource_handler = new ResourceHandler();
            resource_handler.setDirectoriesListed(false);
            try {
                resource_handler.setBaseResource(Resource.newResource(environment.templateDirFile().getPath() + "/assets/"));
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (settings.getAsBoolean("application.session.enable", false)) {
                handlers.setHandlers(new Handler[]{resource_handler, new SessionHandler(), new DefaultHandler()});
            } else {
                handlers.setHandlers(new Handler[]{resource_handler, new DefaultHandler()});
            }

        } else {
            handlers.setHandlers(new Handler[]{new DefaultHandler()});
        }


        server.setHandler(handlers);
    }


    class DefaultHandler extends AbstractHandler {

        @Override
        public void handle(String s, Request request, final HttpServletRequest httpServletRequest, final HttpServletResponse httpServletResponse) throws IOException, ServletException {

            class DefaultResponse implements RestResponse {

                private String content;
                private byte[] contentByte;
                private int status = HttpStatus.HttpStatusOK;
                private String content_type = "application/json; charset=UTF-8";

                public void write(String content) {
                    this.content = content;
                }

                private void configureMimeType(ViewType viewType) {
                    if (viewType == ViewType.xml) {
                        content_type = "application/xml;charset=UTF-8";
                    } else if (viewType == ViewType.image) {
                        content_type = "image/jpeg";
                    } else if (viewType == ViewType.string) {
                        content_type = "text/plain;charset=UTF-8";
                    } else if (viewType == ViewType.html) {
                        content_type = "text/html;charset=UTF-8";
                    }
                }

                @Override
                public void write(String content, ViewType viewType) {
                    configureMimeType(viewType);
                    this.content = content;
                }

                public void write(int httpStatus, String content) {
                    this.content = content;
                    this.status = httpStatus;
                }

                @Override
                public void write(int httpStatus, String content, ViewType viewType) {
                    configureMimeType(viewType);
                    this.content = content;
                    this.status = httpStatus;
                }


                public void write(byte[] contentByte) {
                    this.contentByte = contentByte;
                }

                @Override
                public void cookie(String name, String value) {
                    httpServletResponse.addCookie(new Cookie(name, value));
                }

                @Override
                public void cookie(Map cookieInfo) {
                    Cookie cookie = new Cookie((String) cookieInfo.get("name"), (String) cookieInfo.get("value"));
                    if (cookieInfo.containsKey("domain")) {
                        cookie.setDomain((String) cookieInfo.get("domain"));
                    }
                    if (cookieInfo.containsKey("max_age")) {
                        cookie.setMaxAge((Integer) cookieInfo.get("max_age"));
                    }
                    if (cookieInfo.containsKey("path")) {
                        cookie.setPath((String) cookieInfo.get("path"));
                    }
                    if (cookieInfo.containsKey("secure")) {
                        cookie.setSecure((Boolean) cookieInfo.get("secure"));
                    }

                    if (cookieInfo.containsKey("version")) {
                        cookie.setVersion((Integer) cookieInfo.get("version"));
                    }
                    httpServletResponse.addCookie(cookie);
                }

                public String content() {
                    return this.content;
                }

                @Override
                public Object originContent() {
                    return null;
                }

                private String redirectPath;

                @Override
                public void redirectTo(String path, Map params) {
                    String param = Joiner.on("&").withKeyValueSeparator("=").join(params);
                    if (path.contains("?")) {
                        path += ("&" + param);
                    } else {
                        if (params.size() != 0) {
                            path += ("?" + param);
                        }
                    }
                    this.redirectPath = path;
                }

                @Override
                public RestResponse originContent(Object obj) {
                    return null;
                }

                @Override
                public int status() {
                    return status;
                }

                public void send() throws IOException {
                    httpServletResponse.setContentType(content_type);
                    if (!isNull(redirectPath)) {
                        httpServletResponse.sendRedirect(httpServletResponse.encodeRedirectURL(redirectPath));
                        return;
                    }
                    if (content != null) {
                        output(content);
                        return;
                    }
                    if (contentByte != null) {
                        outputAsByte(contentByte);
                        return;
                    }
                }

                public void error(Exception e) throws IOException {

                    if (e instanceof RecordNotFoundException) {
                        status = HttpStatus.HttpStatusNotFound;
                    } else if (e instanceof RecordExistedException || e instanceof ArgumentErrorException || e instanceof JSONException) {
                        status = HttpStatus.HttpStatusBadRequest;
                    } else {
                        status = HttpStatus.HttpStatusSystemError;
                    }
                    httpServletResponse.setContentType("text/plain;charset=UTF-8");
                    httpServletResponse.setStatus(status);
                    output(e.getMessage());
                }

                public void output(String msg) throws IOException {
                    httpServletResponse.setStatus(status);
                    PrintWriter printWriter = httpServletResponse.getWriter();
                    printWriter.write(msg);
                    printWriter.flush();
                    printWriter.close();
                }

                public void outputAsByte(byte[] msg) throws IOException {
                    //httpServletResponse.setContentType("application/json; charset=UTF-8");
                    httpServletResponse.setStatus(status);
                    ServletOutputStream outputStream = httpServletResponse.getOutputStream();
                    outputStream.write(msg);
                    outputStream.flush();
                    outputStream.close();
                }

                public void internalDispatchRequest() throws Exception {
                    RestController controller = restController;
                    RestRequest restRequest = new DefaultRestRequest(httpServletRequest);
                    HttpServer.setHttpHolder(new HttpHolder(restRequest, this));
                    try {
                        controller.dispatchRequest(restRequest, this);
                    } catch (Exception e) {
                        ExceptionHandler.renderHandle(e);
                    }
                }
            }

            DefaultResponse channel = new DefaultResponse();
            long startTime = System.currentTimeMillis();
            if (!disableMysql) {
                CSDNStatFilterstat.setSQLTIME(new AtomicLong(0l));
            }
            try {
                channel.internalDispatchRequest();
                if (!disableMysql) {
                    JPA.getJPAConfig().getJPAContext().closeTx(false);
                }
                channel.send();
            } catch (Exception e) {
                logger.error(CError.SystemProcessingError, e);
                //回滚
                if (!disableMysql) {
                    try {
                        JPA.getJPAConfig().getJPAContext().closeTx(true);
                    } catch (Exception e2) {
                        //ignore
                    }
                }
                if (restController.errorHandlerKey() != null) {
                    ApplicationController errorApplicationController = ServiceFramwork.injector.getInstance(restController.errorHandlerKey().v1());
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
                    channel.error(e);
                }

            } finally {
                /*
                Completed 200 OK in 1378ms (Views: 45.0ms | ActiveRecord: 34.0ms)

                 */
                boolean logEnable = settings.getAsBoolean("application.log.enable", true);
                if (logEnable) {
                    long endTime = System.currentTimeMillis();
                    String url = httpServletRequest.getQueryString();
                    logger.info("Completed " + channel.status + " in " + (endTime - startTime) + "ms (ActiveORM: " + (disableMysql ? 0 : CSDNStatFilterstat.SQLTIME().get()) + "ms)");
                    logger.info(httpServletRequest.getMethod() +
                            " " + httpServletRequest.getRequestURI() + (isNull(url) ? "" : ("?" + url)));
                    logger.info("\n\n\n\n");
                }
                if (!disableMysql) {
                    CSDNStatFilterstat.removeSQLTIME();
                }
                HttpServer.removeHttpHolder();

            }


        }
    }


    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    server.start();
                    server.join();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        }).start();

    }

    public void close() {
        try {
            server.stop();
        } catch (Exception e) {
            e.printStackTrace();
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
