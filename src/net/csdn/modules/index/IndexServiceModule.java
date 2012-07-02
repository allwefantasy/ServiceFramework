package net.csdn.modules.index;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * User: WilliamZhu
 * Date: 12-6-7
 * Time: 上午10:17
 */
public class IndexServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(IndexService.class).to(DefaultIndexService.class).in(Singleton.class);
    }
}
