package net.csdn.modules.analyzer;/**
 * User: WilliamZhu
 * Date: 12-5-31
 * Time: 下午1:30
 */

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class AnalyzerModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(AnalyzerService.class).to(DefaultAnalyzerService.class).in(Singleton.class);
    }
}
