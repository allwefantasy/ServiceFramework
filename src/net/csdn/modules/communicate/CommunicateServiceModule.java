package net.csdn.modules.communicate;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * User: WilliamZhu
 * Date: 12-6-7
 * Time: 下午9:59
 */
public class CommunicateServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(CommunicateService.class).to(DefaultCommunicateService.class).in(Singleton.class);
    }
}
