package net.csdn.modules.http;

/**
 * User: WilliamZhu
 * Date: 12-6-12
 * Time: 下午10:27
 */
public interface RestResponse {
    public void write(String content);

    public void write(int httpStatus, String content);

    public void write(byte[] content);

    public String content();
}
