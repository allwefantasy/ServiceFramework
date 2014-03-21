package net.csdn.bootstrap.loader.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import javassist.CtClass;
import net.csdn.ServiceFramwork;
import net.csdn.annotation.AnnotationException;
import net.csdn.annotation.Service;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.scan.ScanService;
import net.csdn.common.settings.Settings;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 上午11:32
 */
public class ServiceLoader implements Loader {

    @Override
    public void load(Settings settings) throws Exception {
        final List<Module> moduleList = new ArrayList<Module>();
        for (String item : WowCollections.split2(settings.get("application.service"), ",")) {
            ServiceFramwork.scanService.scanArchives(item, new ScanService.LoadClassEnhanceCallBack() {
                @Override
                public Class loaded(DataInputStream classFile) {
                    try {

                        CtClass ctClass = ServiceFramwork.classPool.makeClass(classFile);
                        if (!ctClass.hasAnnotation(Service.class)) {
                            return null;
                        }
                        final Class clzz = ctClass.toClass();
                        final Service service = (Service) clzz.getAnnotation(Service.class);
                        if (clzz.isInterface() && service.implementedBy() == null)
                            throw new AnnotationException(format("{} no implemented class configured", clzz.getName()));
                        moduleList.add(new AbstractModule() {
                            @Override
                            protected void configure() {
                                if (clzz.isInterface()) {
                                    bind(clzz).to(service.implementedBy()).in(service.value());
                                } else {
                                    bind(clzz).in(service.value());
                                }

                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return null;
                }
            });
        }

        moduleList.addAll(ServiceFramwork.serviceModules);
        ServiceFramwork.AllModules.addAll(moduleList);
    }
}
