package net.csdn.bootstrap.loader.impl;

import javassist.CannotCompileException;
import javassist.CtClass;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.scan.ScanService;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.Enhancer;
import net.csdn.jpa.JPA;
import net.csdn.jpa.enhancer.JPAEnhancer;
import net.csdn.jpa.model.Model;

import javax.persistence.DiscriminatorColumn;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 上午11:29
 */
public class ModelLoader implements Loader {
    @Override
    public void load(Settings settings) throws Exception {
        final Enhancer enhancer = new JPAEnhancer(ServiceFramwork.injector.getInstance(Settings.class));
        final List<CtClass> classList = new ArrayList<CtClass>();
        ServiceFramwork.scanService.scanArchives(settings.get("application.model"), new ScanService.LoadClassEnhanceCallBack() {
            @Override
            public Class loaded(DataInputStream classFile) {
                try {
                    classList.add(enhancer.enhanceThisClass(classFile));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        });

        enhancer.enhanceThisClass2(classList);


        for (CtClass ctClass : classList) {
            if (ctClass.hasAnnotation(DiscriminatorColumn.class)) {
                loadClass(ctClass);
            }
        }

        for (CtClass ctClass : classList) {
            if (!ctClass.hasAnnotation(DiscriminatorColumn.class)) {
                loadClass(ctClass);
            }
        }
    }

    private void loadClass(CtClass ctClass) {
        try {
            Class<Model> clzz = ctClass.toClass();
            JPA.models.put(clzz.getSimpleName(), clzz);
        } catch (CannotCompileException e) {
            e.printStackTrace();
        }
    }
}
