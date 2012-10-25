package net.csdn.mongo;

import com.google.inject.Inject;
import com.mongodb.DB;
import com.mongodb.Mongo;
import net.csdn.common.settings.Settings;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 下午3:21
 * <p/>
 *
 * The +MongoDriver+ class is the wrapper of  MongoDB driver in order to
 * get +DB+ Object.So we can use +DB+ to get collection
 * </p>
 */
public class MongoDriver {

    private Mongo mongo;
    private String dbName;

    private static final String defaultHostName = "127.0.0.1";
    private static final int defaultHostPort = 27017;
    private static final String defaultDBName = "csdn_data_center";

    @Inject
    public MongoDriver(Settings _settings) throws Exception {
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


}
