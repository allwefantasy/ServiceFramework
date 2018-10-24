package net.csdn.bootstrap;

import com.google.inject.Guice;
import com.google.inject.Stage;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.loader.Loader;
import net.csdn.bootstrap.loader.impl.*;
import net.csdn.common.collect.Tuple;
import net.csdn.common.env.Environment;
import net.csdn.common.logging.Loggers;
import net.csdn.common.scan.DefaultScanService;
import net.csdn.common.settings.InternalSettingsPreparer;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;
import net.csdn.modules.dubbo.DubboServer;
import net.csdn.modules.http.HttpServer;
import net.csdn.modules.thrift.ThriftServer;
import net.csdn.mongo.MongoMongo;

import java.util.ArrayList;
import java.util.List;

import static net.csdn.common.collections.WowCollections.isNull;
import static net.csdn.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS;

/**
 * Date: 11-8-31
 * Time: 下午5:34
 */
public class Bootstrap {


    private static HttpServer httpServer;
    private static ThriftServer thriftServer;
    private static DubboServer dubboServer;
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
        Tuple<Settings, Environment> tuple = InternalSettingsPreparer.prepareSettings(EMPTY_SETTINGS, ServiceFramwork.applicaionYamlName());
        if (ServiceFramwork.mode.equals(ServiceFramwork.Mode.development)) {
            ServiceFramwork.mode = ServiceFramwork.Mode.valueOf(tuple.v1().get("mode"));
        }

        Settings settings = tuple.v1();
        boolean disableMysql = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mysql.disable", false);
        boolean disableMongo = settings.getAsBoolean(ServiceFramwork.mode + ".datasources.mongodb.disable", true);
        boolean disableHttp = settings.getAsBoolean("http.disable", false);
        boolean disableThrift = settings.getAsBoolean("thrift.disable", true);
        boolean disableDubbo = settings.getAsBoolean("dubbo.disable", true);

        Loader loggerLoader = new LoggerLoader();

        loggerLoader.load(settings);


        if (ServiceFramwork.scanService.getLoader() == null || (ServiceFramwork.scanService.getLoader() == DefaultScanService.class)) {
            ServiceFramwork.scanService.setLoader(ServiceFramwork.class);
        }
        if (!disableMysql) {
            JPA.configure(new JPA.CSDNORMConfiguration(ServiceFramwork.mode.name(), tuple.v1(), ServiceFramwork.scanService.getLoader(), ServiceFramwork.classPool));
        }
        if (!disableMongo) {
            MongoMongo.configure(new MongoMongo.CSDNMongoConfiguration(ServiceFramwork.mode.name(), tuple.v1(), ServiceFramwork.scanService.getLoader(), ServiceFramwork.classPool));
        }

        Loader moduleLoader = new ModuelLoader();
        moduleLoader.load(settings);

        List<Loader> loaders = new ArrayList<Loader>();

        loaders.add(new ServiceLoader());
        loaders.add(new UtilLoader());
        loaders.add(new ControllerLoader());
        loaders.add(new TemplateLoader());
        if (!ServiceFramwork.mode.equals(ServiceFramwork.Mode.test)) {
            loaders.add(new ThriftLoader());
        }


        for (Loader loader : loaders) {
            loader.load(tuple.v1());
        }

        if (isNull(ServiceFramwork.injector)) {
            ServiceFramwork.injector = Guice.createInjector(Stage.PRODUCTION, ServiceFramwork.AllModules);
        }

        if (!disableMysql) {
            JPA.injector(ServiceFramwork.injector);
        }
        if (!disableMongo) {
            MongoMongo.injector(ServiceFramwork.injector);
        }

        for (Class clzz : ServiceFramwork.startWithSystem) {
            Loggers.getLogger(Bootstrap.class).debug("initialize " + clzz.getName());
            ServiceFramwork.injector.getInstance(clzz);
        }
        isSystemConfigured = true;

        if (!ServiceFramwork.isDisabledThrift()) {
            if (!disableThrift && !ServiceFramwork.mode.equals(ServiceFramwork.Mode.test)) {
                thriftServer = ServiceFramwork.injector.getInstance(ThriftServer.class);
                thriftServer.start();
            }
        }

        if (!ServiceFramwork.isDisableHTTP()) {
            if (!disableHttp && !ServiceFramwork.mode.equals(ServiceFramwork.Mode.test)) {
                httpServer = ServiceFramwork.injector.getInstance(HttpServer.class);
                httpServer.start();
            }
        }

        if (!ServiceFramwork.isDisabledDubbo()) {
            if (!disableDubbo && !ServiceFramwork.mode.equals(ServiceFramwork.Mode.test)) {
                dubboServer = ServiceFramwork.injector.getInstance(DubboServer.class);
            }
        }

        if (!ServiceFramwork.isNoThreadJoin() && (!ServiceFramwork.mode.equals(ServiceFramwork.Mode.test) ||
                dubboServer != null || httpServer != null || thriftServer != null)
                ) {
            Thread.currentThread().join();
        }

    }


}
