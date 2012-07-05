package net.csdn.bootstrap;

import javassist.CtClass;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.bootstrap.loader.impl.*;
import net.csdn.common.collect.Tuple;
import net.csdn.common.settings.InternalSettingsPreparer;
import net.csdn.common.settings.Settings;
import net.csdn.env.Environment;
import net.csdn.jpa.JPA;
import net.csdn.modules.http.HttpServer;

import java.util.ArrayList;
import java.util.List;

import static net.csdn.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS;

/**
 * BlogInfo: william
 * Date: 11-8-31
 * Time: 下午5:34
 */
public class Bootstrap {


    private static HttpServer httpServer;


    public static void main(String[] args) {

        try {
            configureSystem();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(3);
        }
        httpServer = ServiceFramwork.injector.getInstance(HttpServer.class);
        httpServer.start();
        httpServer.join();


    }

    public static void shutdown() {
        if (httpServer != null) {
            httpServer.close();
        }
    }


    //配置整个系统模块
    private static void configureSystem() throws Exception {

        Tuple<Settings, Environment> tuple = InternalSettingsPreparer.prepareSettings(EMPTY_SETTINGS);


        List<Loader> loaders = new ArrayList<Loader>();
        loaders.add(new LoggerLoader());
        loaders.add(new ModuelLoader());
        loaders.add(new ModelLoader());
        loaders.add(new ServiceLoader());
        loaders.add(new UtilLoader());
        loaders.add(new ControllerLoader());
        loaders.add(new ValidatorLoader());

        for (Loader loader : loaders) {
            loader.load(tuple.v1());
        }
        JPA.setSettings(tuple.v1());
    }

//    public static void isLoaded(String name) {
//        java.lang.reflect.Method m = null;
//        try {
//            m = ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[]{String.class});
//            m.setAccessible(true);
//            ClassLoader cl = ClassLoader.getSystemClassLoader();
//            Object test1 = m.invoke(cl, name);
//            System.out.println(name + "=>" + (test1 != null));
//
//            cl = Thread.currentThread().getContextClassLoader();
//            test1 = m.invoke(cl, name);
//            System.out.println(name + "+=>" + (test1 != null));
//            CtClass ctClass = ServiceFramwork.classPool.get(name);
//            System.out.println(ctClass);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
//
//
//    }

}
