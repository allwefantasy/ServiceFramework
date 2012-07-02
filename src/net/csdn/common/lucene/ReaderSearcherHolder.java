package net.csdn.common.lucene;

import net.csdn.CsdnSearchException;
import net.csdn.common.lease.Releasable;
import org.apache.lucene.index.ExtendedIndexSearcher;
import org.apache.lucene.index.IndexReader;

/**
 * User: william
 * Date: 11-9-13
 * Time: 下午1:29
 */
public class ReaderSearcherHolder implements Releasable {

    private final ExtendedIndexSearcher indexSearcher;


    public ReaderSearcherHolder(ExtendedIndexSearcher indexSearcher) {
        this.indexSearcher = indexSearcher;
    }

    public IndexReader reader() {
        return indexSearcher.getIndexReader();
    }

    public ExtendedIndexSearcher searcher() {
        return indexSearcher;
    }

    @Override
    public boolean release() throws CsdnSearchException {
        try {
            indexSearcher.close();
        } catch (Exception e) {
            // do nothing
        }
        try {
            indexSearcher.getIndexReader().close();
        } catch (Exception e) {
            // do nothing
        }
        return true;
    }
}
