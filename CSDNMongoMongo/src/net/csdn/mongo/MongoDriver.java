package net.csdn.mongo;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.mongodb.DB;
import com.mongodb.Mongo;
import javassist.ClassPool;
import javassist.CtClass;
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
 * The +MongoDriver+ class is the wrapper of  MongoDB driver in order to
 * get +DB+ Object.So we can use +DB+ to get collection
 * </p>
 */
public class MongoDriver {

    private Mongo mongo;
    private String dbName;
    public static Injector injector;
    public static ClassPool classPool;

    private static final String defaultHostName = "127.0.0.1";
    private static final int defaultHostPort = 27017;
    private static final String defaultDBName = "csdn_data_center";

    private static Settings settings;



    @Inject
    public MongoDriver(Settings _settings) throws Exception {
        settings = _settings;
        this.mongo = new Mongo(_settings.get("mongo.host", defaultHostName), _settings.getAsInt("mongo.port", defaultHostPort));
        dbName = _settings.get("mongo.database", defaultDBName);
    }

    public Mongo mongo() {
        return mongo;
    }

    public String dbName() {
        return dbName;
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
            final Enhancer enhancer = new MongoEnhancer(settings);
            final List<CtClass> classList = new ArrayList<CtClass>();
            ScanService scanService = new DefaultScanService();
            scanService.setLoader(MongoDriver.class);
            scanService.scanArchives(settings.get("application.document"), new ScanService.LoadClassEnhanceCallBack() {

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
