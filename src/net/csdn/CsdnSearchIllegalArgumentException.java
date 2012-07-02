package net.csdn;

import net.csdn.modules.http.RestStatus;

/**
 * User: william
 * Date: 11-9-5
 * Time: 下午4:28
 */
public class CsdnSearchIllegalArgumentException extends CsdnSearchException {

    public CsdnSearchIllegalArgumentException() {
        super(null);
    }

    public CsdnSearchIllegalArgumentException(String msg) {
        super(msg);
    }

    public CsdnSearchIllegalArgumentException(String msg, Throwable cause) {
        super(msg, cause);
    }

    @Override
    public RestStatus status() {
        return RestStatus.BAD_REQUEST;
    }
}
