package net.csdn.modules.http;

import com.google.inject.Inject;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.exception.ArgumentErrorException;
import net.csdn.exception.ExceptionHandler;
import net.csdn.exception.RecordExistedException;
import net.csdn.exception.RecordNotFoundException;
import net.csdn.jpa.JPA;
import net.csdn.modules.http.support.HttpStatus;
import net.sf.json.JSONException;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.nio.SelectChannelConnector;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

import static net.csdn.common.logging.support.MessageFormat.format;


/**
 * BlogInfo: william
 * Date: 11-9-2
 * Time: 下午1:29
 */
public class HttpServer {
    private Server server;
    private CSLogger logger = Loggers.getLogger(getClass());

    private RestController restController;

    @Inject
    public HttpServer(Settings settings, RestController restController) {
        this.restController = restController;
        server = new Server();
        SelectChannelConnector connector = new SelectChannelConnector();

        connector.setPort(settings.getAsInt("http.port", 8080));
        server.addConnector(connector);

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{new DefaultHandler()});
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

                @Override
                public void write(String content, ViewType viewType) {
                    if (viewType == ViewType.xml) {
                        content_type = "application/xml; charset=UTF-8";
                    }
                    this.content = content;
                }

                public void write(int httpStatus, String content) {
                    this.content = content;
                    this.status = httpStatus;
                }

                @Override
                public void write(int httpStatus, String content, ViewType viewType) {
                    if (viewType == ViewType.xml) {
                        content_type = "application/xml; charset=UTF-8";
                    }
                    this.content = content;
                }


                public void write(byte[] contentByte) {
                    this.contentByte = contentByte;
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
                    //httpServletResponse.setContentLength(msg.length());
                    PrintWriter printWriter = httpServletResponse.getWriter();
                    printWriter.write(msg);
                    printWriter.flush();
                    printWriter.close();
                }

                public void outputAsByte(byte[] msg) throws IOException {
                    //httpServletResponse.setContentType("application/json; charset=UTF-8");
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
                JPA.getJPAConfig().getJPAContext().closeTx(false);
                channel.send();
            } catch (Exception e) {
                e.printStackTrace();
                //回滚
                JPA.getJPAConfig().getJPAContext().closeTx(true);
                channel.error(e);
            }


        }
    }


    public void start() {
        try {
            server.start();
            server.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
