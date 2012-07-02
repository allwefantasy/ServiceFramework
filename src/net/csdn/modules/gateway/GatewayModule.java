package net.csdn.modules.gateway;

import com.google.inject.AbstractModule;
import net.csdn.common.settings.Settings;
import net.csdn.env.Environment;

/**
 * User: WilliamZhu
 * Date: 12-6-7
 * Time: 下午1:51
 */
public class GatewayModule extends AbstractModule {
    private Settings settings;
    private Environment environment;

    public GatewayModule(Settings settings, Environment environment) {
        this.settings = settings;
        this.environment = environment;
    }

    @Override
    protected void configure() {
        bind(GatewayData.class).toInstance(new GatewayData(settings, environment));

    }
}
