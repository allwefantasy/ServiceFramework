package net.csdn.reflect;

import net.csdn.exception.ExceptionHandler;
import org.apache.commons.beanutils.MethodUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static net.csdn.common.collections.WowCollections.list;

/**
 * User: WilliamZhu
 * Date: 12-7-12
 * Time: 下午4:48
 */
public class ReflectHelper {


    public static List<Field> fields(Class clzz, Class annotation) {

        List<Field> result = list();
        Field[] fields = clzz.getFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(annotation)) {
                result.add(field);
            }
        }
        return result;
    }

    public static void field(Object obj, String fieldName, Object value) throws Exception {
        Field field = null;
        Class clzz = obj.getClass();
        try {
            field = clzz.getDeclaredField(fieldName);
        } catch (Exception e) {
            try {


                field = obj.getClass().getField(fieldName);
            } catch (Exception e2) {
                if (clzz.getSuperclass() != null) {
                    field(obj, clzz.getSuperclass(), fieldName, value);
                    return;
                }
                throw e2;
            }
        }
        field.setAccessible(true);
        field.set(obj, value);
    }

    public static Object field(Object obj, String fieldName) throws Exception {
        Field field = null;
        Class clzz = obj.getClass();
        try {
            field = clzz.getDeclaredField(fieldName);
        } catch (Exception e) {
            try {
                field = obj.getClass().getField(fieldName);
            } catch (Exception e2) {
                if (clzz.getSuperclass() != null) {
                    return field(obj, clzz.getSuperclass(), fieldName);
                }
                throw e2;
            }
        }

        field.setAccessible(true);
        return field.get(obj);
    }


    public static void field(Object obj, Class clzz, String fieldName, Object value) throws Exception {
        Field field = null;
        try {
            field = clzz.getDeclaredField(fieldName);
        } catch (Exception e) {
            field = clzz.getField(fieldName);
        }

        field.setAccessible(true);
        field.set(obj, value);
    }

    public static Object field(Object obj, Class clzz, String fieldName) throws Exception {
        Field field = null;
        try {
            field = clzz.getDeclaredField(fieldName);
        } catch (Exception e) {
            field = clzz.getField(fieldName);
        }

        field.setAccessible(true);
        return field.get(obj);
    }

    public static void method2(Object obj, String methodName) {
        try {
            Method method = null;
            try {
                method = obj.getClass().getDeclaredMethod(methodName);
            } catch (Exception e) {
                method = obj.getClass().getMethod(methodName);
            }
            method.setAccessible(true);
            method.invoke(obj);
        } catch (Exception e) {
            try {
                ExceptionHandler.renderHandle(e);
            } catch (Exception e1) {

                e1.printStackTrace();
            }
        }
    }

    public static Object method(Object obj, String methodName) {
        try {
            Method method = null;
            try {
                method = obj.getClass().getDeclaredMethod(methodName);
            } catch (Exception e) {
                method = obj.getClass().getMethod(methodName);
            }
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


    public static Object method(Class obj, String methodName, Object... params) {
        try {

            Method method = null;
            try {
                method = obj.getDeclaredMethod(methodName, paramsToTypes(params));
            } catch (Exception e) {
                try {
                    method = obj.getMethod(methodName, paramsToTypes(params));
                } catch (Exception e1) {
                    method = MethodUtils.getMatchingAccessibleMethod(obj, methodName, paramsToTypes(params));
                }
            }
            method.setAccessible(true);
            return method.invoke(null, params);
        } catch (Exception e) {
            try {
                ExceptionHandler.renderHandle(e);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            return null;
        }
    }

    public static Object method(Object obj, String methodName, Object... params) {
        try {

            Method method = null;
            try {
                method = obj.getClass().getDeclaredMethod(methodName, paramsToTypes(params));
            } catch (Exception e) {
                try {
                    method = obj.getClass().getMethod(methodName, paramsToTypes(params));
                } catch (Exception e1) {
                    method = MethodUtils.getMatchingAccessibleMethod(obj.getClass(), methodName, paramsToTypes(params));
                }
            }
            method.setAccessible(true);
            return method.invoke(obj, params);
        } catch (Exception e) {
            try {
                ExceptionHandler.renderHandle(e);
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            return null;
        }
    }


    public static Class[] paramsToTypes(Object... params) {
        Class[] clzz = new Class[params.length];
        int i = 0;
        for (Object tt : params) {
            clzz[i++] = tt.getClass();
        }
        return clzz;
    }

    public static Annotation[] annotation(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        return field.getAnnotations();
    }

}
