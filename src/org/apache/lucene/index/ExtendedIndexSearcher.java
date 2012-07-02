package org.apache.lucene.index;

import net.csdn.cluster.routing.Shard;
import org.apache.lucene.search.IndexSearcher;

/**
 * User: william
 * Date: 11-9-13
 * Time: 下午1:32
 */
public class ExtendedIndexSearcher extends IndexSearcher {
    private Shard shard;

    public ExtendedIndexSearcher Shard(Shard shard){
        this.shard = shard;
        return this;
    }

    public Shard shard(){
        return shard;
    }

    public ExtendedIndexSearcher(IndexSearcher searcher,Shard shard) {
        super(searcher.getIndexReader());
        setSimilarity(searcher.getSimilarity());
        this.shard = shard;
    }

    public ExtendedIndexSearcher(IndexReader r,Shard shard) {
        super(r);
        this.shard = shard;
    }

    public IndexReader[] subReaders() {
        return this.subReaders;
    }

    public int[] docStarts() {
        return this.docStarts;
    }

    public int readerIndex(int doc) {
        return DirectoryReader.readerIndex(doc, docStarts, subReaders.length);
    }
}
