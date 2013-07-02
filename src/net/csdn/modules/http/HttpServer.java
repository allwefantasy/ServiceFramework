package net.csdn.modules.http;

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
import net.csdn.jpa.JPA;
import net.csdn.modules.http.support.HttpStatus;
import net.sf.json.JSONException;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.resource.Resource;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;


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

    @Inject
    public HttpServer(Settings settings, RestController restController) {
        this.settings = settings;
        this.restController = restController;
        Environment environment = new Environment(settings);
        disableMysql = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mysql.disable", false);
        server = new Server();
        SelectChannelConnector connector = new SelectChannelConnector();

        connector.setPort(settings.getAsInt("http.port", 8080));
        server.addConnector(connector);

        HandlerList handlers = new HandlerList();

        ResourceHandler resource_handler = new ResourceHandler();
        resource_handler.setDirectoriesListed(false);
        try {
            resource_handler.setBaseResource(Resource.newResource(environment.templateDirFile().getPath() + "/assets/"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        handlers.setHandlers(new Handler[]{resource_handler, new DefaultHandler()});
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

                @Override
                public RestResponse originContent(Object obj) {
                    return null;
                }

                @Override
                public int status() {
                    return status;
                }

                public void send() throws IOException {
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


                    httpServletResponse.setStatus(status);
                    output(e.getMessage());
                }

                public void output(String msg) throws IOException {
                    httpServletResponse.setContentType(content_type);
                    httpServletResponse.setStatus(status);
                    //httpServletResponse.setContentLength(msg.length());
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
                    try {
                        controller.dispatchRequest(restRequest, this);
                    } catch (Exception e) {
                        ExceptionHandler.renderHandle(e);
                    }
                }
            }

            DefaultResponse channel = new DefaultResponse();
            try {
                channel.internalDispatchRequest();
                if (!disableMysql) {
                    JPA.getJPAConfig().getJPAContext().closeTx(false);
                }
                channel.send();
            } catch (Exception e) {
                e.printStackTrace();
                //回滚
                if (!disableMysql) {
                    JPA.getJPAConfig().getJPAContext().closeTx(true);
                }
                channel.error(e);
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
