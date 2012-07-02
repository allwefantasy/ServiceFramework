package net.csdn.common.unit;

import net.csdn.CsdnSearchException;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午4:33
 */
public class CsdnSearchParseException extends CsdnSearchException {

    public CsdnSearchParseException(String msg) {
        super(msg);
    }

    public CsdnSearchParseException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
