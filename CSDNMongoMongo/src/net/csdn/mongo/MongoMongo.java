package net.csdn.mongo;

import com.google.inject.Injector;
import com.mongodb.DB;
import com.mongodb.Mongo;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;
import net.csdn.common.scan.DefaultScanService;
import net.csdn.common.scan.ScanService;
import net.csdn.common.settings.Settings;
import net.csdn.mongo.enhancer.Enhancer;
import net.csdn.mongo.enhancer.MongoEnhancer;

import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 下午3:21
 * <p/>
 * <p/>
 * The +MongoMongo+ class is the wrapper of  MongoDB driver in order to
 * get +DB+ Object.So we can use +DB+ to get collection
 * </p>
 */
public class MongoMongo {


    private Mongo mongo;
    private String dbName;

    private static CSDNMongoConfiguration mongoConfiguration;


    private static final String defaultHostName = "127.0.0.1";
    private static final int defaultHostPort = 27017;
    private static final String defaultDBName = "csdn_data_center";


    public MongoMongo(CSDNMongoConfiguration csdnMongoConfiguration) throws Exception {
        this.mongoConfiguration = csdnMongoConfiguration;
        this.mongo = new Mongo(settings().get(mode() + ".datasources.mongodb.host", defaultHostName), mongoConfiguration.settings.getAsInt(mode() + ".datasources.mongodb.port", defaultHostPort));
        dbName = settings().get(mode() + ".datasources.mongodb.database", defaultDBName);
    }


    public static void configure(CSDNMongoConfiguration csdnMongoConfiguration) {
        MongoMongo mongoMongo = null;
        try {
            mongoMongo = new MongoMongo(csdnMongoConfiguration);
            Document.mongoMongo = mongoMongo;
            mongoMongo.loadDocuments();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static String mode() {
        return mongoConfiguration.mode;
    }


    public Mongo mongo() {
        return mongo;
    }

    public String dbName() {
        return dbName;
    }

    public static Settings settings() {
        return mongoConfiguration.settings;
    }

    public static void injector(Injector injector) {
        mongoConfiguration.injector = injector;
    }

    public static class CSDNMongoConfiguration {
        private Settings settings;
        private Injector injector;
        private ClassPool classPool;
        private Class classLoader;

        public Settings getSettings() {
            return settings;
        }

        public void setSettings(Settings settings) {
            this.settings = settings;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        private String mode;

        public CSDNMongoConfiguration(String mode, Settings settings, Class classLoader) {
            this.mode = mode;
            this.classLoader = classLoader;
            this.settings = settings;
            buildClassPool();
        }

        public CSDNMongoConfiguration(String mode, Settings settings, Class classLoader, ClassPool classPool) {
            this.mode = mode;
            this.settings = settings;
            this.classLoader = classLoader;
            this.classPool = classPool;
        }

        public void buildClassPool() {
            classPool = new ClassPool();
            classPool.appendSystemPath();
            classPool.appendClassPath(new LoaderClassPath(classLoader.getClassLoader()));
        }

        public Injector getInjector() {
            return injector;
        }

        public void setInjector(Injector injector) {
            this.injector = injector;
        }

        public ClassPool getClassPool() {
            return classPool;
        }

        public void setClassPool(ClassPool classPool) {
            this.classPool = classPool;
        }
    }

    public static Injector injector() {
        return mongoConfiguration.injector;
    }

    public static ClassPool classPool() {
        return mongoConfiguration.classPool;
    }

    public DB database() {
        return mongo.getDB(dbName);
    }

    public static void loadDocuments() {
        try {
            new MongoDocumentLoader().load();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static class MongoDocumentLoader {

        public void load() throws Exception {
            final Enhancer enhancer = new MongoEnhancer(settings());
            final List<CtClass> classList = new ArrayList<CtClass>();
            ScanService scanService = new DefaultScanService();
            scanService.setLoader(mongoConfiguration.classLoader);
            scanService.scanArchives(settings().get("application.document"), new ScanService.LoadClassEnhanceCallBack() {

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

        }
    }


}
