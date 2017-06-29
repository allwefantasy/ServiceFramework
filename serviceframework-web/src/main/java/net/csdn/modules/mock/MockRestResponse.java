package net.csdn.modules.mock;

import net.csdn.modules.http.RestResponse;
import net.csdn.modules.http.ViewType;
import net.csdn.modules.http.support.HttpStatus;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;


/**
 * User: WilliamZhu
 * Date: 12-7-5
 * Time: 下午5:28
 */
public class MockRestResponse implements RestResponse {
    private String content;
    private Object object;
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

    @Override
    public void cookie(String name, String value) {

    }

    @Override
    public void cookie(Map cookieInfo) {

    }

    public String content() {
        return this.content;
    }

    @Override
    public Object originContent() {
        return object;
    }

    @Override
    public void redirectTo(String path, Map params) {
        //do nothing
    }

    @Override
    public RestResponse originContent(Object obj) {
        this.object = obj;
        return this;
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public PrintWriter printWriter() throws IOException {
        throw new RuntimeException("not implemented yet...");
    }

    @Override
    public ServletOutputStream outputStream() throws IOException {
        throw new RuntimeException("not implemented yet...");
    }

    @Override
    public HttpServletResponse httpServletResponse() throws IOException {
        throw new RuntimeException("not implemented yet...");
    }

    @Override
    public String contentType() {
        throw new RuntimeException("not implemented yet...");
    }

    public void error(Exception e) throws IOException {

    }

    public void output(String msg) throws IOException {

    }

    public void outputAsByte(byte[] msg) throws IOException {

    }


}
