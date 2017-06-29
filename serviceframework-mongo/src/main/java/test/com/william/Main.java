package test.com.william;

import junit.framework.Assert;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.InternalSettingsPreparer;
import net.csdn.common.settings.Settings;
import net.csdn.mongo.MongoMongo;
import test.com.william.document.Blog;

import java.io.InputStream;
import java.util.List;

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
            MongoMongo.CSDNMongoConfiguration csdnMongoConfiguration = new MongoMongo.CSDNMongoConfiguration("development", settings, Main.class);
            MongoMongo.configure(csdnMongoConfiguration);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //now you can use it

        Blog blog = Blog.create(map("userName", "yes", "_id", 1000));
        blog.save();
        blog = Blog.findById(1000);
        Assert.assertTrue("yes".equals(blog.getUserName()));

        Blog blog2 = Blog.create(map("userName", "no", "_id", 1001));
        blog2.save();
        blog2 = Blog.findById(1001);
        Assert.assertTrue("no".equals(blog2.getUserName()));

        List<Blog> blogs = Blog.findAll();
        Assert.assertTrue(blogs.size() >= 2);




    }


}
