package net.csdn.common.collections;

import java.util.HashMap;
import java.util.Map;

/**
 * BlogInfo: william
 * Date: 12-4-16
 * Time: 下午3:11
 */
public class WowMaps<K, V> {
    private Map<K, V> map = new HashMap<K, V>();

    public static WowMaps newHashMap() {
        return new WowMaps();
    }

    public WowMaps<K, V> put(K k, V v) {
        map.put(k, v);
        return this;
    }

    public Map<K, V> map() {
        return map;
    }
}
