package net.csdn.modules.log;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * 10/29/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class SystemLoggerModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SystemLogger.class).to(SystemLoggerImpl.class).in(Singleton.class);
    }
}
