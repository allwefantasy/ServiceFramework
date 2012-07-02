package net.csdn.modules.settings;

import com.google.inject.AbstractModule;
import net.csdn.common.settings.Settings;
import net.csdn.env.Environment;
import net.csdn.modules.parser.filter.FilterParseElement;
import net.csdn.modules.parser.query.QueryParseElement;

/**
 * User: WilliamZhu
 * Date: 12-6-1
 * Time: 下午9:46
 */
public class SettingsModule extends AbstractModule {
    private final Settings settings;
    private final Environment environment;


    public SettingsModule(Settings settings, Environment environment) {
        this.settings = settings;
        this.environment = environment;
    }

    @Override
    protected void configure() {
        bind(Settings.class).toInstance(settings);
        bind(Environment.class).toInstance(environment);
    }
}
