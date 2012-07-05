package net.csdn.bootstrap.loader.impl;

import com.google.inject.*;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.type.DBType;
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
    public void load(final Settings settings) {
        final List<Module> moduleList = new ArrayList<Module>();
        moduleList.add(new SettingsModule(settings));
        moduleList.add(new ThreadPoolModule());
        moduleList.add(new TransportModule());
        moduleList.add(new HttpModule());
        moduleList.add(new ScanModule());


        moduleList.add(new AbstractModule() {
            @Override
            protected void configure() {
                String clzzName = settings.get("type_mapping", "net.csdn.jpa.type.impl.MysqlType");
                final Class czz;
                try {
                    czz = Class.forName(clzzName);
                    bind(DBType.class).to(czz).in(Singleton.class);
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });

        ServiceFramwork.injector = Guice.createInjector(Stage.PRODUCTION, moduleList);
    }
}
