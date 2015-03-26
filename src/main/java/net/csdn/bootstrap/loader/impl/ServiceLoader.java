package net.csdn.bootstrap.loader.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import javassist.CtClass;
import net.csdn.ServiceFramwork;
import net.csdn.annotation.AnnotationException;
import net.csdn.annotation.Service;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.scan.ScanService;
import net.csdn.common.settings.Settings;
import net.csdn.trace.TraceEnhancer;

import java.io.DataInputStream;
import java.util.*;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 上午11:32
 */
public class ServiceLoader implements Loader {
    private CSLogger logger = Loggers.getLogger(ServiceLoader.class);

    @Override
    public void load(final Settings settings) throws Exception {
        final List<Module> moduleList = new ArrayList<Module>();
        final Set<Class> ctClasses = new HashSet<Class>();
        logger.info("scan service package => " + settings.get("application.service"));
        for (String item : WowCollections.split2(settings.get("application.service"), ",")) {

            ServiceFramwork.scanService.scanArchives(item, new ScanService.LoadClassEnhanceCallBack() {
                @Override
                public Class loaded(DataInputStream classFile) {
                    try {

                        CtClass ctClass = ServiceFramwork.classPool.makeClass(classFile);
                        TraceEnhancer.enhanceMethod(ctClass);
                        try {
                            ctClasses.add(ctClass.toClass());
                        } catch (Exception e) {
                            String name = ctClass.getName();
                            Class me = Class.forName(name);
                            if (!ctClasses.contains(me)) {
                                ctClasses.add(me);
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    return null;
                }
            });
        }
        for (final Class clzz : ctClasses) {

            if (clzz.getAnnotation(Singleton.class) != null) {
                logger.info("load  service with @Singleton  => " + clzz.getName());
                moduleList.add(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(clzz).in(Scopes.SINGLETON);
                    }
                });
                continue;
            }
            final Service service = (Service) clzz.getAnnotation(Service.class);

            if (service == null) continue;
            if (clzz.isInterface() && service.implementedBy() == null)
                throw new AnnotationException(format("{} no implemented class configured", clzz.getName()));

            moduleList.add(new AbstractModule() {
                @Override
                protected void configure() {
                    if (clzz.isInterface()) {
                        Map<String,String> singleton = settings.getByPrefix("application.dynamic.implemented.singleton.").getAsMap();
                        Map<String,String> prototype = settings.getByPrefix("application.dynamic.implemented.prototype.").getAsMap();

                        if(singleton.containsKey(clzz.getName())||prototype.containsKey(clzz.getName())){
                            logger.info("service will not loaned because application.dynamic.implemented.singleton or application.dynamic.implemented.prototype configured:  with @Service => " + clzz.getName() + " to " + service.implementedBy().getName() + " in " + service.value().getName());
                        }else{
                            logger.info("load  service with @Service => " + clzz.getName() + " to " + service.implementedBy().getName() + " in " + service.value().getName());
                            bind(clzz).to(service.implementedBy()).in(service.value());
                        }

                    } else {
                        logger.info("load  service with @Service => " + clzz.getName() + " in " + service.value().getName());
                        bind(clzz).in(service.value());
                    }

                }
            });
        }
        for (final Map.Entry<String, String> entry : settings.getByPrefix("application.dynamic.implemented.singleton.").getAsMap().entrySet()) {
            moduleList.add(new AbstractModule() {
                @Override
                protected void configure() {
                    try {
                        Class clzz = Class.forName(entry.getValue());
                        bind(Class.forName(entry.getKey())).to(clzz).in(Scopes.SINGLETON);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            });
        }

        for (final Map.Entry<String, String> entry : settings.getByPrefix("application.dynamic.implemented.prototype.").getAsMap().entrySet()) {
            moduleList.add(new AbstractModule() {
                @Override
                protected void configure() {
                    try {
                        Class clzz = Class.forName(entry.getValue());
                        bind(Class.forName(entry.getKey())).to(clzz).in(Scopes.NO_SCOPE);
                    } catch (ClassNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            });
        }


        logger.info("load service in ServiceFramwork.serviceModules =>" + ServiceFramwork.serviceModules.size());
        moduleList.addAll(ServiceFramwork.serviceModules);
        ServiceFramwork.AllModules.addAll(moduleList);
        logger.info("total load service  =>" + ServiceFramwork.AllModules.size());
    }
}
