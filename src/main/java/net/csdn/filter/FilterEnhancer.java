package net.csdn.filter;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import net.csdn.ServiceFramwork;
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
            return ctClass;
        }

        if (Modifier.isAbstract(ctClass.getModifiers())) return ctClass;

        CtClass controller = classPool.get("net.csdn.modules.http.ApplicationController");

        //copy static fields to subclass.Importance because of inheritance strategy of java
        copyStaticFieldsToSubclass(controller, ctClass);

        //copy static methods to subclass
        copyStaticMethodsToSubclass(controller, ctClass);

        return ctClass;
    }

    private void copyStaticFieldsToSubclass(CtClass document, CtClass targetClass) throws Exception {
        CtField[] ctFields = document.getFields();
        for (CtField ctField : ctFields) {
            if (Modifier.isStatic(ctField.getModifiers()) && ctField.getName().startsWith("parent$_")) {
                CtField ctField1 = new CtField(ctField.getType(), ctField.getName(), targetClass);
                ctField1.setModifiers(ctField.getModifiers());
                targetClass.addField(ctField1);
            }
        }

    }

    private void copyStaticMethodsToSubclass(CtClass document, CtClass targetClass) throws Exception {
        CtMethod[] ctMethods = document.getMethods();

        for (CtMethod ctMethod : ctMethods) {
            if (Modifier.isStatic(ctMethod.getModifiers()) && !shouldNotCopyToSubclassStaticMethods.contains(ctMethod.getName())) {
                CtMethod ctNewMethod = CtNewMethod.copy(ctMethod, targetClass, null);
                targetClass.addMethod(ctNewMethod);
            }

        }
    }

    @Override
    public void enhanceThisClass2(List<CtClass> ctClasses) throws Exception {
        for (CtClass ctClass : ctClasses) {
            if(Modifier.isAbstract(ctClass.getModifiers()))continue;
            ctClass.toClass(ServiceFramwork.scanService.getLoader().getClassLoader(), ServiceFramwork.scanService.getLoader().getProtectionDomain());
        }
    }
}
