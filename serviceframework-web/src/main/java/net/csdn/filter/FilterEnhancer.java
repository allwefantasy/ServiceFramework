package net.csdn.filter;

import javassist.CtClass;
import javassist.CtMethod;
import net.csdn.ServiceFramwork;
import net.csdn.common.enhancer.DynamicBytecode;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.ControllerEnhancer;

import java.io.DataInputStream;
import java.lang.reflect.Modifier;
import java.util.List;

import static net.csdn.common.collections.WowCollections.list;

/**
 * 4/9/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class FilterEnhancer extends ControllerEnhancer {

    private Settings settings;
    private CSLogger logger = Loggers.getLogger(FilterEnhancer.class);


    public FilterEnhancer(Settings settings) {
        this.settings = settings;
    }


    private List<String> shouldNotCopyToSubclassStaticMethods = list(

    );

    @Override
    public CtClass enhanceThisClass(DataInputStream dataInputStream) throws Exception {
        CtClass ctClass = classPool.makeClassIfNew(dataInputStream);
        if (!ctClass.subtypeOf(classPool.get("net.csdn.modules.http.ApplicationController"))) {
            return null;
        }

        if (Modifier.isAbstract(ctClass.getModifiers())) return null;


        CtClass controller = classPool.get("net.csdn.modules.http.ApplicationController");

        //copy static fields to subclass.Importance because of inheritance strategy of java
        DynamicBytecode.copyStaticFields(controller, ctClass, DynamicBytecode.PARENT_STATIC_FIELD_FILTER);

        //copy static methods to subclass
        DynamicBytecode.copyStaticMethods(controller, ctClass, new DynamicBytecode.CtMethodFilter() {
            @Override
            public boolean accept(CtMethod method) {
                return !shouldNotCopyToSubclassStaticMethods.contains(method.getName());
            }
        });

        return ctClass;
    }

    @Override
    public void enhanceThisClass2(List<CtClass> ctClasses) throws Exception {
        for (CtClass ctClass : ctClasses) {
            if (Modifier.isAbstract(ctClass.getModifiers())) continue;
            try {
                ctClass.toClass(ServiceFramwork.scanService.getLoader().getClassLoader(), ServiceFramwork.scanService.getLoader().getProtectionDomain());
            } catch (Exception e) {
                logger.error("Fail to load ctClass " + ctClass.getName(), e);
            }

        }
    }
}
