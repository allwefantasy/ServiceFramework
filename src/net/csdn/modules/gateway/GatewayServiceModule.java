package net.csdn.modules.gateway;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;

/**
 * User: WilliamZhu
 * Date: 12-6-7
 * Time: 下午2:42
 */
public class GatewayServiceModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(GatewayService.class).to(DefaultGatewayService.class).in(Singleton.class);
    }
}
