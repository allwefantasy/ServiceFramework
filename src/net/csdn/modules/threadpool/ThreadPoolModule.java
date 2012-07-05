package net.csdn.modules.threadpool;/**
 * BlogInfo: WilliamZhu
 * Date: 12-5-31
 * Time: 上午11:22
 */

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

public class ThreadPoolModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(ThreadPoolService.class).to(DefaultThreadPoolService.class).in(Singleton.class);
    }
}
