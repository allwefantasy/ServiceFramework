package net.csdn;

import com.google.inject.Injector;
import net.csdn.bootstrap.Bootstrap;
import org.junit.After;
import org.junit.Before;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * User: WilliamZhu
 * Date: 12-6-17
 * Time: 下午10:21
 */
public class BaseServiceWithIocTest {
    protected static Injector injector;


    @Before
    public void setUp() throws Exception {
        //加载Guice容器
        Method method = Bootstrap.class.getDeclaredMethod("configureSystem");
        method.setAccessible(true);
        method.invoke(null);
        injector = ServiceFramwork.injector;
    }

    @After
    public void tearDown() throws Exception {
    }

    /*
       因为很多Service是private的。有时候我们需要替换掉一些Service对象。比如HttpTransportService.
       这样可以避免去访问真实的服务。否则，你可以继承BaseServiceWithJettyTest
     */
    protected void setService(Object targetObj, String fieldName, Object fieldValue) {
        try {
            Field field = targetObj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(targetObj, fieldValue);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void setService(Object targetObj, Class<?> fieldClass, Object fieldValue) {
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
