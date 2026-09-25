package net.csdn.bootstrap.loader.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import javassist.bytecode.ClassFile;
import net.csdn.annotation.AnnotationException;
import net.csdn.annotation.Service;
import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.ClassFiles;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.StartupPhaseTrace;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.scan.ScanService;
import net.csdn.common.settings.Settings;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * Loads service classes with {@link Class#forName(String, boolean, ClassLoader)}.
 * The class file is not defined through Javassist.
 */
public class ServiceLoader implements Loader {
    private CSLogger logger = Loggers.getLogger(ServiceLoader.class);

    @Override
    public void load(final Settings settings) throws Exception {
        final ApplicationContext application = ApplicationContext.require();
        final List<Module> moduleList = new ArrayList<Module>();
        final Map<String, Class<?>> classes = new LinkedHashMap<String, Class<?>>();
        logger.info("scan service package => " + settings.get("application.service"));
        for (String item : WowCollections.split2(settings.get("application.service"), ",")) {
            StartupPhaseTrace.Frame scan = StartupPhaseTrace.open(application.enhancementContext(), "scan.service");
            try {
            application.scanService().scanArchives(item, new ScanService.LoadClassEnhanceCallBack() {
                @Override
                public Class loaded(DataInputStream classFile) {
                    ClassFile parsed = parse(classFile);
                    if (parsed == null || classes.containsKey(parsed.getName())) {
                        return null;
                    }
                    if (!ClassFiles.hasAnnotation(parsed, Service.class.getName())
                            && !ClassFiles.hasAnnotation(parsed, Singleton.class.getName())) {
                        return null;
                    }
                    classes.put(parsed.getName(), load(application.targetLoader(), parsed.getName()));
                    return null;
                }
            });
            } finally {
                scan.close();
            }
        }
        for (final Class clzz : classes.values()) {
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
            if (service == null) {
                continue;
            }
            if (clzz.isInterface() && service.implementedBy() == null) {
                throw new AnnotationException(format("{} no implemented class configured", clzz.getName()));
            }
            moduleList.add(new AbstractModule() {
                @Override
                protected void configure() {
                    if (clzz.isInterface()) {
                        Map<String, String> singleton = settings.getByPrefix("application.dynamic.implemented.singleton.").getAsMap();
                        Map<String, String> prototype = settings.getByPrefix("application.dynamic.implemented.prototype.").getAsMap();
                        if (singleton.containsKey(clzz.getName()) || prototype.containsKey(clzz.getName())) {
                            logger.info("service will not be loaded because dynamic implemented binding is configured: " + clzz.getName());
                        } else {
                            logger.info("load  service with @Service => " + clzz.getName());
                            bind(clzz).to(service.implementedBy()).in(service.value());
                        }
                    } else {
                        logger.info("load  service with @Service => " + clzz.getName());
                        bind(clzz).in(service.value());
                    }
                }
            });
        }
        bindDynamic(settings, moduleList, "application.dynamic.implemented.singleton.", Scopes.SINGLETON);
        bindDynamic(settings, moduleList, "application.dynamic.implemented.prototype.", Scopes.NO_SCOPE);
        logger.info("load service in application.serviceModules =>" + application.serviceModules().size());
        moduleList.addAll(application.serviceModules());
        application.allModules().addAll(moduleList);
        logger.info("total load service  =>" + application.allModules().size());
    }

    private static void bindDynamic(final Settings settings, List<Module> moduleList, final String prefix, final com.google.inject.Scope scope) {
        for (final Map.Entry<String, String> entry : settings.getByPrefix(prefix).getAsMap().entrySet()) {
            moduleList.add(new AbstractModule() {
                @Override
                protected void configure() {
                    try {
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Class service = Class.forName(entry.getKey(), false, ApplicationContext.require().targetLoader());
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Class implementation = Class.forName(entry.getValue(), false, ApplicationContext.require().targetLoader());
                        bind(service).to(implementation).in(scope);
                    } catch (ClassNotFoundException e) {
                        throw new EnhancementFailure(
                                EnhancementFailure.Category.CONFIGURATION,
                                entry.getValue(),
                                null,
                                "scan",
                                "dynamic service class was not found",
                                e);
                    }
                }
            });
        }
    }

    private static ClassFile parse(DataInputStream classFile) {
        try {
            return ClassFiles.parse(ClassFiles.read(classFile));
        } catch (IOException e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.SCAN,
                    null,
                    null,
                    "scan",
                    "service class bytes were not read",
                    e);
        }
    }

    private static Class<?> load(ClassLoader loader, String name) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.SCAN,
                    name,
                    null,
                    "scan",
                    "service class was not found in the target loader",
                    e);
        }
    }
}
