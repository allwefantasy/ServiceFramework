package net.csdn;

import net.csdn.modules.http.RestStatus;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午2:22
 */
public class CsdnSearchException extends RuntimeException {
    /**
     * Construct a <code>CsdnSearchException</code> with the specified detail message.
     *
     * @param msg the detail message
     */
    public CsdnSearchException(String msg) {
        super(msg);
    }

    /**
     * Construct a <code>CsdnSearchException</code> with the specified detail message
     * and nested exception.
     *
     * @param msg   the detail message
     * @param cause the nested exception
     */
    public CsdnSearchException(String msg, Throwable cause) {
        super(msg, cause);
    }

    /**
     * Returns the rest status code associated with this exception.
     */
    public RestStatus status() {
        CsdnSearchException current = this;
        while (current instanceof CsdnSearchWrapperException) {
            if (getCause() == null) {
                break;
            }
            if (getCause() instanceof CsdnSearchException) {
                current = (CsdnSearchException) getCause();
            } else {
                break;
            }
        }
        if (current == this) {
            return RestStatus.INTERNAL_SERVER_ERROR;
        } else {
            return current.status();
        }
    }
}
