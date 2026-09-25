package net.csdn.bootstrap;

/**
 * BlogInfo: william
 * Date: 11-8-31
 * Time: 下午5:35
 */
public class Application {
    public static void main(String[] args) {
        try {
            Class<?> bootstrap = Class.forName("net.csdn.bootstrap.Bootstrap", false, Application.class.getClassLoader());
            java.lang.reflect.Method method = bootstrap.getMethod("main", new Class[]{args.getClass()});
            method.invoke(null, new Object[]{args});
        } catch (Exception e) {
            e.printStackTrace();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

    }
}
