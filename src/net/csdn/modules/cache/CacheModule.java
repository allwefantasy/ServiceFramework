package net.csdn.modules.cache;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * User: WilliamZhu
 * Date: 12-6-28
 * Time: 上午7:42
 */
public class CacheModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(RedisClient.class).in(Singleton.class);
    }
}
