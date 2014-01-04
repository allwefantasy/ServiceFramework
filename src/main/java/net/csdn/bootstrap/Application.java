package net.csdn.bootstrap;

import javassist.CtClass;
import net.csdn.ServiceFramwork;

import java.lang.reflect.Method;

/**
 * BlogInfo: william
 * Date: 11-8-31
 * Time: 下午5:35
 */
public class Application {
    public static void main(String[] args) {
        try {
            CtClass ctClass = ServiceFramwork.classPool.get("net.csdn.bootstrap.Bootstrap");
            Method method = ctClass.toClass().getMethod("main", new Class[]{args.getClass()});
            method.invoke(null, new Object[]{args});
        } catch (Exception e) {
            e.printStackTrace();
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

    }
}
