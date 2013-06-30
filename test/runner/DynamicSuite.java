package runner;

import javassist.CtClass;
import net.csdn.ServiceFramwork;
import net.csdn.common.settings.Settings;
import org.junit.runners.Suite;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-7-27
 * Time: 下午3:29
 */
public class DynamicSuite extends Suite {
    private static Class[] testClasses = new Class[]{};

    static {
        try {
            initEnv();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public DynamicSuite(Class<?> setupClass) throws Exception {
        super(setupClass, findTestClass());
    }

    public static void initEnv() throws Exception {
        ServiceFramwork.mode = ServiceFramwork.Mode.test;
        CtClass ctClass = ServiceFramwork.classPool.get("net.csdn.bootstrap.Bootstrap");
        //加载Guice容器
        Method method = ctClass.toClass().getDeclaredMethod("configureSystem");
        method.setAccessible(true);
        method.invoke(null);
    }

    public static Class[] findTestClass() throws Exception {
        Settings settings = ServiceFramwork.injector.getInstance(Settings.class);
        List<Class> classList = new ArrayList<Class>();
        List<String> classStrs = ServiceFramwork.scanService.classNames(settings.get("application.test"), DynamicSuite.class);
        for (String abc : classStrs) {
            classList.add(Class.forName(abc));
        }
        Class[] classes = testClasses.length > 0 ? testClasses : new Class[classList.size()];
        classList.toArray(classes);
        return classes;
    }
}
