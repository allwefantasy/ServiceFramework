package net.csdn.exception;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午4:40
 */
public class FailedToResolveConfigException extends RuntimeException {
    public FailedToResolveConfigException(String msg) {
        super(msg);
    }

    public FailedToResolveConfigException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
