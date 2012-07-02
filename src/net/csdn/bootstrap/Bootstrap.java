package net.csdn.bootstrap;

import com.google.inject.*;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;
import net.csdn.annotation.AnnotationException;
import net.csdn.annotation.At;
import net.csdn.annotation.Service;
import net.csdn.annotation.Util;
import net.csdn.common.Classes;
import net.csdn.common.collect.Tuple;
import net.csdn.common.logging.log4j.LogConfigurator;
import net.csdn.common.settings.InternalSettingsPreparer;
import net.csdn.common.settings.Settings;
import net.csdn.enhancers.Enhancer;
import net.csdn.env.Environment;
import net.csdn.jpa.JPA;
import net.csdn.jpa.context.JPAEnhancer;
import net.csdn.modules.analyzer.AnalyzerModule;
import net.csdn.modules.communicate.CommunicateService;
import net.csdn.modules.communicate.CommunicateServiceModule;
import net.csdn.modules.deduplicate.DuplicateFilterServiceModule;
import net.csdn.modules.factory.FactoryModule;
import net.csdn.modules.gateway.*;
import net.csdn.modules.highlight.HighlightServiceModule;
import net.csdn.modules.http.*;
import net.csdn.modules.index.IndexServiceModule;
import net.csdn.modules.parser.ParserModule;
import net.csdn.modules.persist.PersistServiceModule;
import net.csdn.modules.scan.DefaultScanService;
import net.csdn.modules.scan.ScanModule;
import net.csdn.modules.scan.ScanService;
import net.csdn.modules.search.SearchServiceModule;
import net.csdn.modules.settings.SettingsModule;
import net.csdn.modules.spam.SpamFilterServiceModule;
import net.csdn.modules.threadpool.ThreadPoolModule;
import net.csdn.modules.transport.TransportModule;
import org.hibernate.metamodel.domain.Entity;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS;

/**
 * User: william
 * Date: 11-8-31
 * Time: 下午5:34
 */
public class Bootstrap {

    public static Injector injector;
    private static HttpServer httpServer;
    static ScanService scanService = new DefaultScanService();
    public static ClassPool classPool;

    static {
        classPool = new ClassPool();
        classPool.appendSystemPath();
        classPool.appendClassPath(new LoaderClassPath(Bootstrap.class.getClassLoader()));
    }

    public static void main(String[] args) {


        try {
            configureSystem();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(3);
        }

        httpServer = injector.getInstance(HttpServer.class);
        httpServer.start();
        httpServer.join();


    }

    public static void shutdown() {
        if (httpServer != null) {
            httpServer.close();
        }
    }


    private static void configLogger(Tuple<Settings, Environment> tuple) throws Exception {
        Classes.getDefaultClassLoader().loadClass("org.apache.log4j.Logger");
        LogConfigurator.configure(tuple.v1());
    }

    private static void configModule(Tuple<Settings, Environment> tuple) {
        final List<Module> moduleList = new ArrayList<Module>();
        moduleList.add(new SettingsModule(tuple.v1(), tuple.v2()));
        moduleList.add(new ThreadPoolModule());
        moduleList.add(new TransportModule());
        moduleList.add(new HttpModule());
        moduleList.add(new ScanModule());

        injector = Guice.createInjector(Stage.PRODUCTION, moduleList);
    }

    private static void configController(Tuple<Settings, Environment> tuple) throws Exception {
        Settings settings = tuple.v1();
        final List<Module> moduleList = new ArrayList<Module>();

        //自动加载所有Action类
        scanService.scanArchives(settings.get("application.controller"), new ScanService.LoadClassEnhanceCallBack() {
            @Override
            public Class loaded(ClassPool classPool, DataInputStream classFile) {
                try {
                    CtClass ctClass = classPool.makeClass(classFile);
                    moduleList.add(bindAction(ctClass.toClass()));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return null;
            }
        });
        injector = injector.createChildInjector(moduleList);
    }

    //配置整个系统模块
    private static void configureSystem() throws Exception {

        Tuple<Settings, Environment> tuple = InternalSettingsPreparer.prepareSettings(EMPTY_SETTINGS);
        JPA.setSettings(tuple.v1());

        configLogger(tuple);

        configModule(tuple);

        configModel(tuple);

        configService(tuple);

        configUtil(tuple);

        configController(tuple);

    }

    private static void configUtil(Tuple<Settings, Environment> tuple) throws Exception {
        Settings settings = tuple.v1();
        final List<Module> moduleList = new ArrayList<Module>();

        scanService.scanArchives(settings.get("application.util"), new ScanService.LoadClassEnhanceCallBack() {
            @Override
            public Class loaded(ClassPool classPool, DataInputStream classFile) {
                try {
                    CtClass ctClass = classPool.makeClass(classFile);
                    if (!ctClass.hasAnnotation(Util.class)) {
                        return null;
                    }
                    final Class clzz = ctClass.toClass();
                    final Util util = (Util) clzz.getAnnotation(Util.class);
                    if (clzz.isInterface())
                        throw new AnnotationException(format("{} util should not be interface", clzz.getName()));
                    moduleList.add(new AbstractModule() {
                        @Override
                        protected void configure() {
                            bind(clzz).in(util.value());
                        }
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return null;
            }
        });

        injector = injector.createChildInjector(moduleList);
    }

    private static void configModel(Tuple<Settings, Environment> tuple) throws Exception {

        final Enhancer enhancer = new JPAEnhancer(injector.getInstance(Settings.class));
        scanService.scanArchives(tuple.v1().get("application.model"), new ScanService.LoadClassEnhanceCallBack() {
            @Override
            public Class loaded(ClassPool classPool, DataInputStream classFile) {
                try {
                    enhancer.enhanceThisClass(classFile);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }
        });
    }

    private static void configService(Tuple<Settings, Environment> tuple) throws Exception {

        Settings settings = tuple.v1();
        final List<Module> moduleList = new ArrayList<Module>();
        scanService.scanArchives(settings.get("application.service"), new ScanService.LoadClassEnhanceCallBack() {
            @Override
            public Class loaded(ClassPool classPool, DataInputStream classFile) {
                try {

                    CtClass ctClass = classPool.makeClass(classFile);
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


        injector = injector.createChildInjector(moduleList);
    }

    private static Module bindAction(final Class clzz) {
        return new AbstractModule() {
            @Override
            protected void configure() {
                if (clzz == null) return;
                try {
                    Method[] methods = clzz.getDeclaredMethods();

                    for (Method method : methods) {
                        if (method.getModifiers() == Modifier.PRIVATE) continue;
                        At at = method.getAnnotation(At.class);
                        if (at == null) continue;
                        String url = at.path()[0];
                        RestRequest.Method[] httpMethods = at.types();
                        RestController restController = injector.getInstance(RestController.class);
                        for (RestRequest.Method httpMethod : httpMethods) {
                            Tuple<Class<BaseRestHandler>, Method> tuple = new Tuple<Class<BaseRestHandler>, Method>(clzz, method);
                            restController.registerHandler(httpMethod, url, tuple);
                        }
                        bind(clzz);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
    }

}
