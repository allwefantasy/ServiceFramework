package net.csdn.bootstrap.loader.impl;

import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.settings.Settings;
import net.csdn.modules.thrift.ThriftModule;

/**
 * 6/4/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class ThriftLoader implements Loader {

    @Override
    public void load(Settings settings) throws Exception {
        ServiceFramwork.injector = ServiceFramwork.injector.createChildInjector(new ThriftModule());
    }
}
