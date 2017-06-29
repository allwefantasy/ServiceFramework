package net.csdn.common.scan;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-4
 * Time: 下午3:12
 */
public class ScanModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ScanService.class).to(DefaultScanService.class).in(Singleton.class);
    }
}
