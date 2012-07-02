package net.csdn.modules.highlight;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * User: WilliamZhu
 * Date: 12-6-9
 * Time: 上午10:23
 */
public class HighlightServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(HighlightService.class).in(Singleton.class);
    }
}
