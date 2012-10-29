package test.com.example;

import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.InternalSettingsPreparer;
import net.csdn.common.settings.Settings;
import net.csdn.mongo.MongoMongo;
import test.com.example.document.Blog;

import java.io.InputStream;

import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-29
 * Time: 下午5:01
 */
public class Main {
    public static void main(String[] args) {
        //find the config file

        InputStream inputStream = Main.class.getResourceAsStream("application_for_test.yml");
        Settings settings = InternalSettingsPreparer.simplePrepareSettings(ImmutableSettings.Builder.EMPTY_SETTINGS,
                inputStream);

        //configure MongoMongo
        try {
            MongoMongo.CSDNMongoConfiguration csdnMongoConfiguration = new MongoMongo.CSDNMongoConfiguration("development", settings, Main.class.getClassLoader());
            MongoMongo.configure(csdnMongoConfiguration);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //now you can use it
        Blog blog = Blog.create(map("userName", "yes", "_id", 1000));
        blog.save();
        blog = Blog.findById(1000);
        System.out.println(blog.getUserName());

    }


}
