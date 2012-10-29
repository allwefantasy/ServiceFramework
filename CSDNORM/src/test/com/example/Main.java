package test.com.example;

import javassist.ClassPool;
import javassist.LoaderClassPath;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.InternalSettingsPreparer;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;
import test.com.example.model.Tag;

import java.io.InputStream;

/**
 * User: WilliamZhu
 * Date: 12-10-29
 * Time: 下午3:38
 * <p/>
 * here show how to use CSDNORM in your standalone application
 */
public class Main {

    public static void main(String[] args) {
        //find the config file

        InputStream inputStream = Main.class.getResourceAsStream("application_for_test.yml");
        Settings settings = InternalSettingsPreparer.simplePrepareSettings(ImmutableSettings.Builder.EMPTY_SETTINGS,
                inputStream);

        //configure ORM
        JPA.mode = "development";
        JPA.setSettings(settings);

        JPA.classPool = new ClassPool();
        JPA.classPool.appendSystemPath();
        JPA.classPool.appendClassPath(new LoaderClassPath(Main.class.getClassLoader()));


        JPA.classLoader = Main.class.getClassLoader();

        //finally load all you model
        JPA.loadModels();

        Tag.findAll();
        //then you can use you pojo now

    }

}
