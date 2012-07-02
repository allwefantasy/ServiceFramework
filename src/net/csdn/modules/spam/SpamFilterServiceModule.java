package net.csdn.modules.spam;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * User: WilliamZhu
 * Date: 12-6-12
 * Time: 下午11:01
 */
public class SpamFilterServiceModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(SpamFilterService.class).to(DefaultSpamFilterService.class).in(Singleton.class);
    }
}
