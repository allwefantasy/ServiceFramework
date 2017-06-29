package net.csdn.common.reflect;

import com.google.common.collect.Lists;
import net.csdn.common.exception.ExceptionHandler;
import org.apache.commons.beanutils.MethodUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.csdn.common.collections.WowCollections.list;

/**
 * User: WilliamZhu
 * Date: 12-7-12
 * Time: 下午4:48
 */
public class ReflectHelper {


    public static List<Field> fields(Class clzz, Class annotation) {

        List<Field> result = list();
        Field[] fields = clzz.getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(annotation)) {
                result.add(field);
            }
        }
        return result;
    }

    public static List<Method> methods(Class clzz, Class annotation) {

        List<Method> result = list();
        Method[] methods = clzz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(annotation)) {
                result.add(method);
            }
        }
        return result;
    }

    public static List<Field> findFieldsByAnnotation(Class clzz, Class annotation) {

        return fields(clzz, annotation);
    }

    public static List<Method> findMethodsByAnnotation(Class clzz, Class annotation) {

        return methods(clzz, annotation);
    }

    public static Field findField(Class clzz, String fieldName) {
        Field field = null;
        try {
            field = clzz.getDeclaredField(fieldName);
        } catch (Exception e) {
            if (clzz.getSuperclass() != null) {
                field = findField(clzz.getSuperclass(), fieldName);
            }
        }
        return field;
    }

    public static Method findMethodByName(Class clzz, String methodName) {
        Method result = null;
        Method[] methods = clzz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                result = method;
                break;
            }
        }
        if (result == null) {
            if (clzz.getSuperclass() != null) {
                result = findMethodByName(clzz.getSuperclass(), methodName);
            }
        }
        return result;
    }

    public static void field(Object obj, String fieldName, Object value) throws Exception {
        Field field = findField(obj.getClass(), fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    public static Object field(Object obj, String fieldName) throws Exception {
        Field field = findField(obj.getClass(), fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }

    public static Object staticField(Class obj, String fieldName) {
        Field field = findField(obj, fieldName);
        field.setAccessible(true);
        try {
            return field.get(null);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void staticField(Class obj, String fieldName, Object value) {
        Field field = findField(obj, fieldName);
        field.setAccessible(true);
        try {
            field.set(null, value);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }


    public static void field(Object obj, Class clzz, String fieldName, Object value) throws Exception {
        Field field = findField(clzz, fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    public static Object field(Object obj, Class clzz, String fieldName) throws Exception {
        Field field = findField(clzz, fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }

    public static Method findTTMethod(Class clazz, String methodName, Class... paramTypes) {
        Class superclass = clazz;
        try {
            return superclass.getDeclaredMethod(methodName, paramTypes);
        } catch (Exception e2) {
            do {
                superclass = superclass.getSuperclass();
                try {
                    return superclass.getDeclaredMethod(methodName, paramTypes);
                } catch (NoSuchMethodException e) {
                    continue;
                }
            }
            while (superclass != null && !Object.class.getName().equals(superclass.getName()));
        }

        return null;
    }

    public static List<Method> findTTMethods(Class clazz, String methodName) {
        Set<Method> methodSet = new HashSet();
        Class superclass = clazz;
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                methodSet.add(method);
            }
        }

        do {
            superclass = superclass.getSuperclass();
            for (Method method : superclass.getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    methodSet.add(method);
                }
            }
        }
        while (superclass != null && !Object.class.getName().equals(superclass.getName()));
        return Lists.newArrayList(methodSet);
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

    public static Object staticMethod(Class obj, String methodName, Object... params) {
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
            if (!Modifier.isPublic(method.getModifiers())) {
                method.setAccessible(true);
            }
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
