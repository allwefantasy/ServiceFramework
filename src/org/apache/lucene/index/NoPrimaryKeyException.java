package org.apache.lucene.index;

import net.csdn.CsdnSearchException;

/**
 * User: william
 * Date: 11-9-15
 * Time: 下午4:17
 */
public class NoPrimaryKeyException extends CsdnSearchException {

    public NoPrimaryKeyException(String msg) {
        super(msg);
    }

    public NoPrimaryKeyException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
