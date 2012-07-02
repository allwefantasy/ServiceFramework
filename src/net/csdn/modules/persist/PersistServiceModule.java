package net.csdn.modules.persist;

import com.google.inject.AbstractModule;
import com.google.inject.Singleton;
import net.csdn.common.settings.Settings;
import net.csdn.modules.persist.mongodb.DefaultMongoService;
import net.csdn.modules.persist.mongodb.MongoClient;
import net.csdn.modules.persist.mongodb.MongoService;
import net.csdn.modules.persist.mysql.DataSourceManager;
import net.csdn.modules.persist.mysql.MysqlService;

/**
 * User: WilliamZhu
 * Date: 12-6-1
 * Time: 下午9:29
 */
public class PersistServiceModule extends AbstractModule {
    private Settings settings;

    public PersistServiceModule(Settings settings) {
        this.settings = settings;
    }

    @Override
    protected void configure() {
        bind(DataSourceManager.class).asEagerSingleton();
        bind(MysqlService.class).in(Singleton.class);
        bind(MongoClient.class).in(Singleton.class);
        bind(MongoService.class).to(DefaultMongoService.class).in(Singleton.class);
        bind(PersistService.class).in(Singleton.class);

    }
}
