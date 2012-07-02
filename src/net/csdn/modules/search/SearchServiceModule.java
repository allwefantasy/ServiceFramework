package net.csdn.modules.search;/**
 * User: WilliamZhu
 * Date: 12-5-31
 * Time: 下午2:40
 */

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class SearchServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SearchService.class).to(DefaultSearchService.class).in(Singleton.class);
    }
}
