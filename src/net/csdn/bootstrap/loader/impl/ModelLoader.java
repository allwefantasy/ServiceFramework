package net.csdn.bootstrap.loader.impl;

import javassist.ClassPool;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.Enhancer;
import net.csdn.jpa.enhancer.JPAEnhancer;
import net.csdn.modules.scan.ScanService;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * User: WilliamZhu
 * Date: 12-7-2
 * Time: 上午11:29
 */
public class ModelLoader implements Loader {
    @Override
    public void load(Settings settings) throws IOException {
        final Enhancer enhancer = new JPAEnhancer(ServiceFramwork.injector.getInstance(Settings.class));
        ServiceFramwork.scanService.scanArchives(settings.get("application.model"), new ScanService.LoadClassEnhanceCallBack() {
            @Override
            public Class loaded(ClassPool classPool, DataInputStream classFile) {
                try {
                    enhancer.enhanceThisClass(classFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        });
    }
}
