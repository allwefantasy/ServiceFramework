package net.csdn.modules.deduplicate;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import net.csdn.modules.deduplicate.service.BloomFilterService;

/**
 * User: WilliamZhu
 * Date: 12-6-8
 * Time: 下午9:31
 */
public class DuplicateFilterServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(DuplicateFilterService.class).to(BloomDuplicateFilterService.class).in(Singleton.class);
    }
}
