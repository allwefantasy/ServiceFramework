package net.csdn.bootstrap.loader.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Module;
import javassist.bytecode.ClassFile;
import net.csdn.annotation.AnnotationException;
import net.csdn.annotation.Util;
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
import java.util.List;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * Loads util classes with {@link Class#forName(String, boolean, ClassLoader)}.
 * Bytes are not passed to {@code toClass}.
 */
public class UtilLoader implements Loader {
    private CSLogger logger = Loggers.getLogger(UtilLoader.class);

    @Override
    public void load(Settings settings) throws Exception {
        final ApplicationContext application = ApplicationContext.require();
        final List<Module> moduleList = new ArrayList<Module>();
        for (String item : WowCollections.split2(settings.get("application.util"), ",")) {
            StartupPhaseTrace.Frame scan = StartupPhaseTrace.open(application.enhancementContext(), "scan.util");
            try {
            application.scanService().scanArchives(item, new ScanService.LoadClassEnhanceCallBack() {
                @Override
                public Class loaded(DataInputStream classFile) {
                    try {
                        ClassFile parsed = ClassFiles.parse(ClassFiles.read(classFile));
                        if (!ClassFiles.hasAnnotation(parsed, Util.class.getName())) {
                            return null;
                        }
                        logger.info("util load :    " + parsed.getName());
                        final Class clzz = Class.forName(parsed.getName(), false, application.targetLoader());
                        final Util util = (Util) clzz.getAnnotation(Util.class);
                        if (clzz.isInterface()) {
                            throw new AnnotationException(format("{} util should not be interface", clzz.getName()));
                        }
                        moduleList.add(new AbstractModule() {
                            @Override
                            protected void configure() {
                                bind(clzz).in(util.value());
                            }
                        });
                        return null;
                    } catch (EnhancementFailure failure) {
                        throw failure;
                    } catch (Exception e) {
                        throw new EnhancementFailure(
                                EnhancementFailure.Category.SCAN,
                                null,
                                null,
                                "scan",
                                "util class was not loaded",
                                e);
                    }
                }
            });
            } finally {
                scan.close();
            }
        }
        application.allModules().addAll(moduleList);
    }
}
