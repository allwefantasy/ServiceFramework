package net.csdn.bootstrap.loader.impl;

import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.Classes;
import net.csdn.common.logging.log4j.LogConfigurator;
import net.csdn.common.settings.Settings;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 上午11:37
 */
public class LoggerLoader implements Loader {
    @Override
    public void load(Settings settings) throws Exception {
        Classes.getDefaultClassLoader().loadClass("org.apache.log4j.Logger");
        LogConfigurator.configure(settings);
    }
}
