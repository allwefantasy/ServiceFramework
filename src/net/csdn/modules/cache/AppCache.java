package net.csdn.modules.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import net.csdn.common.settings.Settings;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 4/13/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class AppCache {

    private Settings settings;

    private Cache<String, Object> defaultCache;

    private Map<String, LoadingCache> cacheMap;

    @Inject
    public AppCache(Settings settings) {
        this.settings = settings;
        defaultCache = CacheBuilder.newBuilder().
                expireAfterWrite(settings.getAsInt("cache.refresh.minutes", 2), TimeUnit.MINUTES).
                maximumSize(settings.getAsInt("cache.maximumSize", 10000)).
                build();
        cacheMap = new ConcurrentHashMap<String, LoadingCache>();
    }

    public <T> T fetch(String name, Callable<T> callable) {
        try {
            return (T) defaultCache.get(name, callable);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    public <K, V> AppCache buildCache(String name, CacheLoader<K, V> cacheLoader) {
        LoadingCache<K, V> cache = CacheBuilder.newBuilder().
                refreshAfterWrite(settings.getAsInt("cache.refresh.minutes", 2), TimeUnit.MINUTES).
                maximumSize(settings.getAsInt("cache.maximumSize", 10000)).
                build(cacheLoader);
        cacheMap.put(name, cache);
        return this;
    }

    public LoadingCache cache(String name) {
        return cacheMap.get(name);
    }

}
