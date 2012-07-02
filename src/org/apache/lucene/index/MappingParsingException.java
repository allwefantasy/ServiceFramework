package org.apache.lucene.index;

import net.csdn.common.unit.CsdnSearchParseException;

/**
 * User: william
 * Date: 11-9-15
 * Time: 下午3:41
 */
public class MappingParsingException extends CsdnSearchParseException {
    public MappingParsingException(String msg) {
        super(msg);
    }

    public MappingParsingException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
