package net.csdn.cluster.routing.djb;

import net.csdn.cluster.routing.HashFunction;

import java.io.Serializable;

/**
 * User: william
 * Date: 11-9-1
 * Time: 上午11:15
 */
public class DjbHashFunction implements HashFunction, Serializable {
    @Override
    public int hash(String value) {
        long hash = 5381;

        for (int i = 0; i < value.length(); i++) {
            hash = ((hash << 5) + hash) + value.charAt(i);
        }

        return (int) hash;
    }

    @Override
    public int hash(String type, String id) {
        long hash = 5381;

        for (int i = 0; i < type.length(); i++) {
            hash = ((hash << 5) + hash) + type.charAt(i);
        }

        for (int i = 0; i < id.length(); i++) {
            hash = ((hash << 5) + hash) + id.charAt(i);
        }

        return (int) hash;
    }
}
