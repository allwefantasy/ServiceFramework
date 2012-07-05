package net.csdn.modules.mock;

import net.csdn.modules.http.*;
import net.csdn.modules.http.support.HttpStatus;

import java.io.IOException;


/**
 * User: WilliamZhu
 * Date: 12-7-5
 * Time: 下午5:28
 */
public class MockRestResponse implements RestResponse {
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


    public void error(Exception e) throws IOException {

    }

    public void output(String msg) throws IOException {

    }

    public void outputAsByte(byte[] msg) throws IOException {

    }


}
