package net.csdn.common.collect;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import java.util.Map;

import static com.google.common.collect.Maps.newHashMap;

/**
 * BlogInfo: william
 * Date: 11-9-1
 * Time: 下午2:15
 */
public class MapBuilder<K, V> {
    public static <K, V> MapBuilder<K, V> newMapBuilder() {
        return new MapBuilder<K, V>();
    }

    public static <K, V> MapBuilder<K, V> newLinkedHashMap() {
        return new MapBuilder<K, V>(Maps.newLinkedHashMap());
    }

    public static <K, V> MapBuilder<K, V> newMapBuilder(Map<K, V> map) {
        return new MapBuilder<K, V>().putAll(map);
    }

    private Map<K, V> map = newHashMap();

    public MapBuilder() {
        this.map = newHashMap();
    }

    public MapBuilder(Map map) {
        this.map = map;
    }

    public MapBuilder<K, V> putAll(Map<K, V> map) {
        this.map.putAll(map);
        return this;
    }

    public MapBuilder<K, V> put(K key, V value) {
        this.map.put(key, value);
        return this;
    }

    public MapBuilder<K, V> remove(K key) {
        this.map.remove(key);
        return this;
    }

    public V get(K key) {
        return map.get(key);
    }

    public boolean containsKey(K key) {
        return map.containsKey(key);
    }

    public Map<K, V> map() {
        return this.map;
    }

    public ImmutableMap<K, V> immutableMap() {
        return ImmutableMap.copyOf(map);
    }
}
