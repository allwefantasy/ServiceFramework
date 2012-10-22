package net.csdn.mongo;

import com.google.inject.Inject;
import com.mongodb.DB;
import com.mongodb.Mongo;
import net.csdn.common.settings.Settings;

/**
 * User: WilliamZhu
 * Date: 12-10-17
 * Time: 下午3:21
 */
public class MongoDriver {

    private Mongo mongo;
    private String defaultDBName;

    @Inject
    public MongoDriver(Settings _settings) throws Exception {
        this.mongo = new Mongo(_settings.get("mongo.host", "127.0.0.1"), _settings.getAsInt("mongo.port", 27017));
        defaultDBName = _settings.get("mongo.database", "csdn_data_center");
    }

    public Mongo mongo() {
        return mongo;
    }

    public String defaultDBName() {
        return defaultDBName;
    }


    public DB database() {
        return mongo.getDB(defaultDBName);
    }


}
