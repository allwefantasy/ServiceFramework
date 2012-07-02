package net.csdn.modules.settings;

import com.google.inject.AbstractModule;
import net.csdn.common.settings.Settings;

/**
 * User: WilliamZhu
 * Date: 12-6-1
 * Time: 下午9:46
 */
public class SettingsModule extends AbstractModule {
    private final Settings settings;


    public SettingsModule(Settings settings) {
        this.settings = settings;
    }

    @Override
    protected void configure() {
        bind(Settings.class).toInstance(settings);
    }
}
