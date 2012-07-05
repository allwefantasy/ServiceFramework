package net.csdn.bootstrap.loader.impl;

import com.google.inject.Guice;
import com.google.inject.Module;
import com.google.inject.Stage;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.settings.Settings;
import net.csdn.modules.http.HttpModule;
import net.csdn.modules.scan.ScanModule;
import net.csdn.modules.settings.SettingsModule;
import net.csdn.modules.threadpool.ThreadPoolModule;
import net.csdn.modules.transport.TransportModule;

import java.util.ArrayList;
import java.util.List;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 上午11:32
 */
public class ModuelLoader implements Loader {
    @Override
    public void load(Settings settings) {
        final List<Module> moduleList = new ArrayList<Module>();
        moduleList.add(new SettingsModule(settings));
        moduleList.add(new ThreadPoolModule());
        moduleList.add(new TransportModule());
        moduleList.add(new HttpModule());
        moduleList.add(new ScanModule());

        ServiceFramwork.injector = Guice.createInjector(Stage.PRODUCTION, moduleList);
    }
}
