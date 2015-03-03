package net.csdn.modules.http;

import com.google.common.base.Joiner;
import com.google.common.collect.Maps;
import net.csdn.common.exception.ArgumentErrorException;
import net.csdn.common.exception.RecordExistedException;
import net.csdn.common.exception.RecordNotFoundException;
import net.csdn.modules.http.support.HttpStatus;
import net.sf.json.JSONException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.isNull;

public class DefaultResponse implements RestResponse {

    private String content;
    private byte[] contentByte;
    private int status = HttpStatus.HttpStatusOK;
    private String content_type = "application/json; charset=UTF-8";

    private HttpServletRequest httpServletRequest;
    private HttpServletResponse httpServletResponse;
    private RestController restController;

    public DefaultResponse(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, RestController restController) {
        this.httpServletRequest = httpServletRequest;
        this.httpServletResponse = httpServletResponse;
        this.restController = restController;
    }

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
        Map temp = Maps.newHashMap();
        try {
            for (Object o : params.keySet()) {
                if (params.get(o) instanceof String) {
                    temp.put(o, URLEncoder.encode((String) params.get(o), "UTF-8"));
                } else {
                    temp.put(o, params.get(o));
                }
            }
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        String param = Joiner.on("&").withKeyValueSeparator("=").join(temp);

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
        if (content == null) {
            output("null");
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

    public PrintWriter printWriter() throws IOException {
        return httpServletResponse.getWriter();
    }

    public ServletOutputStream outputStream() throws IOException {
        return httpServletResponse.getOutputStream();
    }

    public HttpServletResponse httpServletResponse() throws IOException {
        return httpServletResponse;
    }


    public void outputAsByte(byte[] msg) throws IOException {
        //httpServletResponse.setContentType("application/json; charset=UTF-8");
        httpServletResponse.setStatus(status);
        ServletOutputStream outputStream = httpServletResponse.getOutputStream();
        outputStream.write(msg);
        outputStream.flush();
        outputStream.close();
    }


}