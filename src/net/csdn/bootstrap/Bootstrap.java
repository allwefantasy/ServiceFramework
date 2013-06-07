package net.csdn.bootstrap;

import javassist.CtClass;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.bootstrap.loader.impl.*;
import net.csdn.common.collect.Tuple;
import net.csdn.common.env.Environment;
import net.csdn.common.scan.DefaultScanService;
import net.csdn.common.settings.InternalSettingsPreparer;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;
import net.csdn.modules.http.HttpServer;
import net.csdn.modules.thrift.ThriftServer;
import net.csdn.mongo.MongoMongo;

import java.util.ArrayList;
import java.util.List;

import static net.csdn.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS;

/**
 * Date: 11-8-31
 * Time: 下午5:34
 */
public class Bootstrap {


    private static HttpServer httpServer;
    private static ThriftServer thriftServer;
    private static boolean isSystemConfigured = false;

    public static void main(String[] args) {

        try {
            configureSystem();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(3);
        }
    }

    public static void shutdown() {
        if (httpServer != null) {
            httpServer.close();
        }
        if (thriftServer != null) {
            thriftServer.stop();
        }
    }


    //配置整个系统模块
    private static void configureSystem() throws Exception {
        if (isSystemConfigured) return;
        Tuple<Settings, Environment> tuple = InternalSettingsPreparer.prepareSettings(EMPTY_SETTINGS);
        if (ServiceFramwork.mode.equals(ServiceFramwork.Mode.development)) {
            ServiceFramwork.mode = ServiceFramwork.Mode.valueOf(tuple.v1().get("mode"));
        }

        Settings settings = tuple.v1();
        boolean disableMysql = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mysql.disable", false);
        boolean disableMongo = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mongodb.disable", false);
        boolean disableHttp = settings.getAsBoolean("http.disable", false);
        boolean disableThrift = settings.getAsBoolean("thrift.disable", false);


        if (ServiceFramwork.scanService.getLoader() == null || (ServiceFramwork.scanService.getLoader() == DefaultScanService.class)) {
            ServiceFramwork.scanService.setLoader(ServiceFramwork.class);
        }
        if (!disableMysql) {
            JPA.configure(new JPA.CSDNORMConfiguration(ServiceFramwork.mode.name(), tuple.v1(), ServiceFramwork.scanService.getLoader(), ServiceFramwork.classPool));
        }
        if (!disableMongo) {
            MongoMongo.configure(new MongoMongo.CSDNMongoConfiguration(ServiceFramwork.mode.name(), tuple.v1(), ServiceFramwork.scanService.getLoader(), ServiceFramwork.classPool));
        }

        Loader loggerLoader = new LoggerLoader();
        Loader moduleLoader = new ModuelLoader();
        loggerLoader.load(settings);
        moduleLoader.load(settings);


        List<Loader> loaders = new ArrayList<Loader>();

        loaders.add(new ServiceLoader());
        loaders.add(new UtilLoader());
        loaders.add(new ControllerLoader());
        if (!ServiceFramwork.mode.equals(ServiceFramwork.Mode.test)) {
            loaders.add(new ThriftLoader());
        }


        for (Loader loader : loaders) {
            loader.load(tuple.v1());
        }


        if (!disableMysql) {
            JPA.injector(ServiceFramwork.injector);
        }
        if (!disableMongo) {
            MongoMongo.injector(ServiceFramwork.injector);
        }

        isSystemConfigured = true;


        if (!disableThrift && !ServiceFramwork.mode.equals(ServiceFramwork.Mode.test)) {
            thriftServer = ServiceFramwork.injector.getInstance(ThriftServer.class);
            thriftServer.start();
        }

        if (!disableHttp && !ServiceFramwork.mode.equals(ServiceFramwork.Mode.test)) {
            httpServer = ServiceFramwork.injector.getInstance(HttpServer.class);
            httpServer.start();
        }
        if ((!disableHttp || !disableThrift) && !ServiceFramwork.mode.equals(ServiceFramwork.Mode.test)) {
            Thread.currentThread().join();
        }

    }


    public static void isLoaded(String name) {
        java.lang.reflect.Method m = null;
        try {
            m = ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[]{String.class});
            m.setAccessible(true);
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            Object test1 = m.invoke(cl, name);
            System.out.println(name + "=>" + (test1 != null));

            cl = Thread.currentThread().getContextClassLoader();
            test1 = m.invoke(cl, name);
            System.out.println(name + "+=>" + (test1 != null));
            if (test1 != null) {

            }
            CtClass ctClass = ServiceFramwork.classPool.get(name);
            System.out.println(cl);
            System.out.println(ctClass);
            System.out.println("-------------------------------");

        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}
