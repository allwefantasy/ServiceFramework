package org.apache.lucene.index;

/**
 * User: william
 * Date: 11-9-13
 * Time: 下午2:34
 */
public class EngineException extends IndexException {
    public EngineException(Index index, String msg) {
        super(index, msg);
    }

    public EngineException(Index index, String msg, Throwable cause) {
        super(index, msg, cause);
    }

    protected EngineException(Index index, boolean withSpace, String msg, Throwable cause) {
        super(index, withSpace, msg, cause);
    }
}
