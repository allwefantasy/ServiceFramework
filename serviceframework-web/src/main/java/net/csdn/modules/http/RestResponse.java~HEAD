package net.csdn.modules.http;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-12
 * Time: 下午10:27
 */
public interface RestResponse {

    public void write(String content);

    public void write(String content, ViewType viewType);

    public void write(int httpStatus, String content);

    public void write(int httpStatus, String content, ViewType viewType);

    public void write(byte[] content);

    public void cookie(String name, String value);

    public void cookie(Map cookieInfo);

    public String content();

    public Object originContent();

    public void redirectTo(String path, Map params);

    public RestResponse originContent(Object obj);

    public int status();

    public PrintWriter printWriter() throws IOException;

    public ServletOutputStream outputStream() throws IOException;

    public HttpServletResponse httpServletResponse() throws IOException;

    public String contentType();
}
