package net.csdn.reflect;

import net.csdn.exception.ExceptionHandler;
import net.csdn.exception.RenderFinish;
import net.csdn.modules.http.ApplicationController;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-7-12
 * Time: 下午4:48
 */
public class ReflectHelper {
    public static void field(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    public static Object field(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }


    public static Object method(Object obj, String methodName) {
        try {
            Method method = obj.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method.invoke(obj);
        } catch (Exception e) {
            try {
                ExceptionHandler.renderHandle(e);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            return null;
        }
    }

    public static void method(Object obj, String methodName, Object... params) {
        try {
            Class[] clzz = new Class[params.length];

            int i = 0;
            for (Object tt : params) {
                clzz[i++] = tt.getClass();
            }
            Method method = obj.getClass().getDeclaredMethod(methodName, clzz);
            method.setAccessible(true);
            method.invoke(obj, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Annotation[] annotation(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        return field.getAnnotations();
    }

}
