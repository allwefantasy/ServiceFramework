package net.csdn.modules.deduplicate.algorithm.bloomfilter;


import net.csdn.modules.transport.data.SearchHit;

import java.io.Serializable;
import java.util.Collection;
import java.util.Random;

/**
 * User: william
 * Date: 12-4-13
 * Time: 上午11:44
 */
public class FixSizedBloomFilter<E> implements Serializable {
    private static int k = 4;
    private static int byte_size = 300;
    public byte[] bitSet = null;
    public int doc;
    public E searchHit;


    public FixSizedBloomFilter(String[] _chunks, int _doc, E searchHit) {
        this.doc = _doc;
        bitSet = new byte[byte_size];
        this.searchHit = searchHit;
        for (String chunk : _chunks) {
            add(chunk);
        }
    }

    public FixSizedBloomFilter(String[] _chunks) {
        bitSet = new byte[byte_size];
        for (String chunk : _chunks) {
            add(chunk);
        }
    }

    public FixSizedBloomFilter(byte[] _bitSet, int _doc) {
        this.doc = _doc;
        this.bitSet = _bitSet;
    }

    public FixSizedBloomFilter(byte[] _bitSet) {
        this.bitSet = _bitSet;
    }


    public boolean add(String o) {
        Random r = new Random(o.hashCode());
        for (int x = 0; x < k; x++) {
            int wow = r.nextInt(byte_size << 3);
            bitSet[wow >> 3] |= (1 << (7 - wow % 8));
        }
        return false;
    }


    public boolean addAll(Collection<String> c) {
        for (String o : c) {
            add(o);
        }
        return false;
    }


    public void clear() {
        for (int x = 0; x < byte_size; x++) {
            bitSet[x] = 0;
        }
    }
}
