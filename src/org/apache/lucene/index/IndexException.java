package org.apache.lucene.index;

import net.csdn.CsdnSearchException;

/**
 * User: william
 * Date: 11-9-13
 * Time: 下午2:34
 */
public class IndexException extends CsdnSearchException{
    private final Index index;

    public IndexException(Index index,String msg) {
        this(index,msg,null);
    }

    public IndexException(Index index, String msg, Throwable cause) {
        this(index,true,msg,cause);
    }
    protected IndexException(Index index, boolean withSpace, String msg, Throwable cause) {
        super("[" + (index == null ? "_na" : index.name()) + "]" + (withSpace ? " " : "") + msg, cause);
        this.index = index;
    }
    public Index index() {
        return index;
    }
}
