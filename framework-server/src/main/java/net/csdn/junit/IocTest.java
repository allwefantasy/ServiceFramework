package net.csdn.junit;

import com.google.inject.Injector;
import javassist.CtClass;
import net.csdn.ServiceFramwork;
import net.csdn.jpa.JPA;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-17
 * Time: 下午10:21
 */
public class IocTest {

    protected static Injector injector = ServiceFramwork.injector;


    public static boolean checkClassLoaded(String name) throws Exception {
        Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", new Class[]{String.class});
        m.setAccessible(true);
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        Object test1 = m.invoke(cl, name);
        return test1 != null;


    }

    public static void initEnv(Class classLoader) {
        try {
            ServiceFramwork.mode = ServiceFramwork.Mode.test;
            ServiceFramwork.scanService.setLoader(classLoader);
            CtClass ctClass = ServiceFramwork.classPool.get("net.csdn.bootstrap.Bootstrap");
            if (checkClassLoaded(ctClass.getName())) {
                return;
            }
            //加载Guice容器
            Method method = ctClass.toClass().getDeclaredMethod("configureSystem");
            method.setAccessible(true);
            method.invoke(null);
            injector = ServiceFramwork.injector;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void dbCommit() {
        JPA.getJPAConfig().getJPAContext().closeTx(false);
    }

    public void commitTransaction() {
        dbCommit();
    }

    /*
      因为很多Service是private的。有时候我们需要替换掉一些Service对象。比如HttpTransportService.
      这样可以避免去访问真实的服务。否则，你可以继承BaseServiceWithJettyTest
    */
    protected void mockService(Object targetObj, String fieldName, Object fieldValue) {
        try {
            Field field = targetObj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(targetObj, fieldValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public <T> T findController(Class<T> clzz) {
        return injector.getInstance(clzz);
    }

    public <T> T findService(Class<T> clzz) {
        return injector.getInstance(clzz);
    }

    protected void mockService(Object targetObj, Class<?> fieldClass, Object fieldValue) {
        try {

            Field[] fields = targetObj.getClass().getDeclaredFields();
            for (Field field : fields) {
                Class temp = field.getType();
                if (temp == fieldClass || fieldClass.isAssignableFrom(temp)) {
                    field.setAccessible(true);
                    field.set(targetObj, fieldValue);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
