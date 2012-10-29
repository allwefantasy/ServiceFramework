package net.csdn.jpa;


import com.google.inject.Injector;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import net.csdn.common.collect.Tuple;
import net.csdn.common.env.Environment;
import net.csdn.common.io.Streams;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.scan.DefaultScanService;
import net.csdn.common.scan.ScanService;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.Enhancer;
import net.csdn.jpa.context.JPAConfig;
import net.csdn.jpa.enhancer.JPAEnhancer;
import net.csdn.jpa.model.Model;
import net.csdn.jpa.type.DBInfo;
import net.csdn.jpa.type.DBType;
import net.csdn.jpa.type.impl.MysqlType;

import javax.persistence.DiscriminatorColumn;
import java.io.DataInputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-26
 * Time: 下午9:46
 * 因为无法通过IOC管理jpa生成的对象(当然可以通过AOP解决,避免复杂，我们这里采用静态工厂,这个会在系统启动的时候设置值)
 * 目前不支持多数据库
 */
public class JPA {
    private static JPAConfig jpaConfig;

    private static CSLogger logger = Loggers.getLogger(JPA.class);
    private static Settings settings;
    private static Environment environment;

    public final static Map<String, Class<Model>> models = new HashMap<String, Class<Model>>();

    public static ClassLoader classLoader;
    public static String mode;
    public static ClassPool classPool;
    public static Injector injector;

    public final static DBType dbType = new MysqlType();
    public static DBInfo dbInfo;

    public static synchronized JPAConfig getJPAConfig() {
        if (jpaConfig == null) {
            try {
                modifyPersistenceXml(new Tuple<Settings, Environment>(settings, environment));
            } catch (Exception e) {
                e.printStackTrace();
            }
            jpaConfig = new JPAConfig(properties(), settings.get("datasources.mysql.database"));
        }
        return jpaConfig;
    }

    //自动同步application.xml文件的配置到persistence.xml
    private static void modifyPersistenceXml(Tuple<Settings, Environment> tuple) throws Exception {

        String fileContent = Streams.copyToStringFromClasspath(classLoader, "META-INF/persistence.xml");
        Map<String, Settings> groups = tuple.v1().getGroups(mode + ".datasources");
        Settings mysqlSetting = groups.get("mysql");
        //
        StringBuffer stringBuffer = new StringBuffer();
        for (Class clzz : models.values()) {
            stringBuffer.append(format("<class>{}</class>", clzz.getName()));
        }
        String path = classLoader.getResource("META-INF/persistence.xml").getPath();
        Streams.copy(format(fileContent, mysqlSetting.get("database"), stringBuffer.toString()), new FileWriter(path));
    }

    public static void setJPAConfig(JPAConfig _jpaConfig) {
        jpaConfig = _jpaConfig;
    }

    public static Settings getSettings() {
        return settings;
    }

    public static void setSettings(Settings settings) {
        JPA.settings = settings;
        dbInfo = new DBInfo(settings);
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }


    private static Map<String, String> properties() {
        Map<String, String> properties = new HashMap<String, String>();

        Map<String, Settings> groups = settings.getGroups(mode + ".datasources");
        Settings mysqlSetting = groups.get("mysql");

        properties.put("hibernate.show_sql", settings.get("orm.show_sql", "true"));
        properties.put("hibernate.connection.driver_class", "com.mysql.jdbc.Driver");
        properties.put("hibernate.connection.password", mysqlSetting.get("password"));
        properties.put("hibernate.connection.url", "jdbc:mysql://" + mysqlSetting.get("host") + "/" + mysqlSetting.get("database"));
        properties.put("hibernate.connection.username", mysqlSetting.get("username"));
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.put("hibernate.c3p0.min_size", settings.get("orm.pool_min_size", "20"));
        properties.put("hibernate.c3p0.max_size", settings.get("orm.pool_max_size", "20"));
        properties.put("hibernate.c3p0.timeout", settings.get("orm.timeout", "300"));
        properties.put("hibernate.c3p0.max_statements", settings.get("orm.max_statements", "50"));
        properties.put("hibernate.c3p0.idle_test_period", settings.get("orm.idle_test_period", "3000"));
        //    properties.put("hibernate.query.factory_class", "org.hibernate.hql.internal.classic.ClassicQueryTranslatorFactory");
        return properties;
    }

    public static void loadModels() {
        try {
            new JPAModelLoader().load();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class JPAModelLoader {
        public void load() throws Exception {
            final Enhancer enhancer = new JPAEnhancer(JPA.getSettings());

            final List<CtClass> classList = new ArrayList<CtClass>();
            ScanService scanService = new DefaultScanService();
            scanService.setLoader(JPA.class);
            scanService.scanArchives(settings.get("application.model"), new ScanService.LoadClassEnhanceCallBack() {
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
}
