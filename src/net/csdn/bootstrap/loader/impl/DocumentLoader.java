package net.csdn.bootstrap.loader.impl;

import javassist.ClassPool;
import javassist.CtClass;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.Enhancer;
import net.csdn.modules.scan.ScanService;
import net.csdn.mongo.enhancer.MongoEnhancer;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-10-18
 * Time: 上午11:11
 */
public class DocumentLoader implements Loader {

    @Override
    public void load(Settings settings) throws Exception {
        final Enhancer enhancer = new MongoEnhancer(ServiceFramwork.injector.getInstance(Settings.class));
        final List<CtClass> classList = new ArrayList<CtClass>();
        ServiceFramwork.scanService.scanArchives(settings.get("application.document"), new ScanService.LoadClassEnhanceCallBack() {
            @Override
            public Class loaded(ClassPool classPool, DataInputStream classFile) {
                try {
                    classList.add(enhancer.enhanceThisClass(classFile));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        });

        enhancer.enhanceThisClass2(classList);

    }
}
