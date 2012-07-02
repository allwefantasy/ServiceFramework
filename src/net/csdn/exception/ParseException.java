package net.csdn.exception;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午4:33
 */
public class ParseException extends RuntimeException {

    public ParseException(String msg) {
        super(msg);
    }

    public ParseException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
