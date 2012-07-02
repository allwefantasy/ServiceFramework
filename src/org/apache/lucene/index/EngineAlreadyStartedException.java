package org.apache.lucene.index;

/**
 * User: william
 * Date: 11-9-13
 * Time: 下午2:39
 */
public class EngineAlreadyStartedException extends IndexException {
    public EngineAlreadyStartedException(Index index) {
        super(index, "Already started");
    }
}
