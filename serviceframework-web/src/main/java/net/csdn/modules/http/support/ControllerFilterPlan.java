package net.csdn.modules.http.support;

import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.FieldInfo;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.annotation.Annotation;
import net.csdn.annotation.filter.AfterFilter;
import net.csdn.annotation.filter.AroundFilter;
import net.csdn.annotation.filter.BeforeFilter;
import net.csdn.annotation.rest.At;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.filter.FilterHelper;
import net.csdn.modules.http.WowAroundFilter;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds an immutable filter catalog from class-file order plus the
 * LinkedHashMap filled by {@code beforeFilter} / {@code aroundFilter} /
 * {@code afterFilter}. Reflection arrays are not used as the order source.
 * A missing filter or a wrong signature fails here, before the port opens.
 */
public final class ControllerFilterPlan {

    private final Map<Class<?>, Map<Method, FilterChain>> byClass;

    private ControllerFilterPlan(Map<Class<?>, Map<Method, FilterChain>> byClass) {
        this.byClass = byClass;
    }

    public static ControllerFilterPlan compile(List<Class<?>> controllers) {
        Map<Class<?>, Map<Method, FilterChain>> byClass =
                new IdentityHashMap<Class<?>, Map<Method, FilterChain>>();
        if (controllers != null) {
            for (int i = 0; i < controllers.size(); i++) {
                Class<?> controller = controllers.get(i);
                byClass.put(controller, Collections.unmodifiableMap(buildChains(controller)));
            }
        }
        return new ControllerFilterPlan(Collections.unmodifiableMap(byClass));
    }

    public Map<Method, FilterChain> chains(Class<?> controller) {
        Map<Method, FilterChain> chains = byClass.get(controller);
        if (chains == null) {
            return Collections.emptyMap();
        }
        return chains;
    }

    public FilterChain chain(Class<?> controller, Method action) {
        Map<Method, FilterChain> chains = byClass.get(controller);
        if (chains == null || !chains.containsKey(action)) {
            throw failure(controller.getName(), "filter metadata was not compiled for " + action.getName());
        }
        return chains.get(action);
    }

    public boolean contains(Class<?> controller) {
        return byClass.containsKey(controller);
    }

    @SuppressWarnings("unchecked")
    private static Map<Method, FilterChain> buildChains(Class<?> controller) {
        ClassFile file = classFile(controller);
        List<Method> actions = actions(controller, file);
        List<String> beforeFields = annotatedFields(file, BeforeFilter.class.getName());
        List<String> aroundFields = annotatedFields(file, AroundFilter.class.getName());
        List<String> afterFields = annotatedFields(file, AfterFilter.class.getName());
        Map<String, Map<String, List<?>>> beforeStatic = staticFilters(controller, "parent$_before_filter_info");
        Map<String, Map<String, List<?>>> aroundStatic = staticFilters(controller, "parent$_around_filter_info");
        Map<String, Map<String, List<?>>> afterStatic = staticFilters(controller, "parent$_after_filter_info");
        Map<Method, FilterChain> result = new LinkedHashMap<Method, FilterChain>();
        for (int i = 0; i < actions.size(); i++) {
            Method action = actions.get(i);
            List<Method> before = resolve(controller, action.getName(), beforeFields, beforeStatic, new Class<?>[0]);
            List<Method> around = resolve(controller, action.getName(), aroundFields, aroundStatic,
                    new Class<?>[]{WowAroundFilter.class});
            List<Method> after = resolve(controller, action.getName(), afterFields, afterStatic, new Class<?>[0]);
            result.put(action, new FilterChain(before, around, after));
        }
        return result;
    }

    private static List<Method> resolve(
            Class<?> controller,
            String action,
            List<String> fieldNames,
            Map<String, Map<String, List<?>>> staticFilters,
            Class<?>[] parameters) {
        List<Method> methods = new ArrayList<Method>();
        for (int i = 0; i < fieldNames.size(); i++) {
            String fieldName = fieldNames.get(i);
            String methodName = fieldName.startsWith("_") ? fieldName.substring(1) : fieldName;
            if (methodName.length() == 0) {
                throw failure(controller.getName(), "filter field " + fieldName + " has no method name");
            }
            Map<String, List<?>> info = fieldInfo(controller, fieldName);
            if (applies(controller, methodName, info, action)) {
                add(methods, require(controller, methodName, parameters));
            }
        }
        for (Map.Entry<String, Map<String, List<?>>> entry : staticFilters.entrySet()) {
            if (applies(controller, entry.getKey(), entry.getValue(), action)) {
                add(methods, require(controller, entry.getKey(), parameters));
            }
        }
        return methods;
    }

    private static void add(List<Method> methods, Method method) {
        for (int i = 0; i < methods.size(); i++) {
            if (methods.get(i).equals(method)) {
                return;
            }
        }
        methods.add(method);
    }

    private static Method require(Class<?> controller, String name, Class<?>[] parameters) {
        List<Method> named = new ArrayList<Method>();
        Class<?> current = controller;
        while (current != null && current != Object.class) {
            Method[] declared = current.getDeclaredMethods();
            for (int i = 0; i < declared.length; i++) {
                if (name.equals(declared[i].getName())) {
                    named.add(declared[i]);
                }
            }
            if (!named.isEmpty()) {
                break;
            }
            current = current.getSuperclass();
        }
        if (named.isEmpty()) {
            throw failure(controller.getName(), "filter method " + name + signature(parameters) + " does not exist");
        }
        for (int i = 0; i < named.size(); i++) {
            Method method = named.get(i);
            if (Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (matches(method.getParameterTypes(), parameters)) {
                return method;
            }
        }
        StringBuilder found = new StringBuilder();
        for (int i = 0; i < named.size(); i++) {
            if (i > 0) {
                found.append(", ");
            }
            found.append(named.get(i).getName()).append(signature(named.get(i).getParameterTypes()));
        }
        throw failure(controller.getName(),
                "filter method " + name + signature(parameters) + " has the wrong signature; found " + found);
    }

    private static boolean matches(Class<?>[] actual, Class<?>[] expected) {
        if (actual.length != expected.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (actual[i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<?>> fieldInfo(Class<?> controller, String fieldName) {
        Class<?> current = controller;
        while (current != null && current != Object.class) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                if (!Modifier.isStatic(field.getModifiers())) {
                    throw failure(controller.getName(), "filter field " + fieldName + " must be static");
                }
                field.setAccessible(true);
                Object value = field.get(null);
                if (value == null) {
                    return Collections.emptyMap();
                }
                if (!(value instanceof Map)) {
                    throw failure(controller.getName(), "filter field " + fieldName + " must hold a Map");
                }
                return (Map<String, List<?>>) value;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw failure(controller.getName(), "filter field " + fieldName + " is not readable", e);
            }
        }
        throw failure(controller.getName(), "filter field " + fieldName + " does not exist");
    }

    private static boolean applies(Class<?> controller, String filter, Map<String, List<?>> info, String action) {
        if (info == null || info.isEmpty()) {
            return true;
        }
        if (info.containsKey(FilterHelper.BeforeFilter.only)) {
            return strings(controller, filter, info.get(FilterHelper.BeforeFilter.only)).contains(action);
        }
        if (info.containsKey(FilterHelper.BeforeFilter.except)) {
            return !strings(controller, filter, info.get(FilterHelper.BeforeFilter.except)).contains(action);
        }
        return true;
    }

    private static List<String> strings(Class<?> controller, String filter, Object value) {
        if (!(value instanceof List)) {
            throw failure(controller.getName(), "filter " + filter + " only/except must be a list of action names");
        }
        List<?> raw = (List<?>) value;
        List<String> names = new ArrayList<String>();
        for (int i = 0; i < raw.size(); i++) {
            if (!(raw.get(i) instanceof String)) {
                throw failure(controller.getName(), "filter " + filter + " only/except must be a list of action names");
            }
            names.add((String) raw.get(i));
        }
        return names;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, List<?>>> staticFilters(Class<?> controller, String accessor) {
        Method method = findAccessor(controller, accessor);
        if (method == null) {
            return Collections.emptyMap();
        }
        try {
            method.setAccessible(true);
            Object value = method.invoke(null);
            if (value == null) {
                return Collections.emptyMap();
            }
            if (!(value instanceof Map)) {
                throw failure(controller.getName(), accessor + " did not return a Map");
            }
            Map<?, ?> map = (Map<?, ?>) value;
            Map<String, Map<String, List<?>>> ordered = new LinkedHashMap<String, Map<String, List<?>>>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof Map)) {
                    throw failure(controller.getName(), accessor + " entries must be filter name to Map");
                }
                ordered.put((String) entry.getKey(), (Map<String, List<?>>) entry.getValue());
            }
            return ordered;
        } catch (EnhancementFailure failure) {
            throw failure;
        } catch (Exception e) {
            throw failure(controller.getName(), accessor + " could not be read", e);
        }
    }

    private static Method findAccessor(Class<?> controller, String name) {
        Class<?> current = controller;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static List<Method> actions(Class<?> controller, ClassFile file) {
        List<Method> actions = new ArrayList<Method>();
        List<?> methods = file.getMethods();
        for (int i = 0; i < methods.size(); i++) {
            MethodInfo info = (MethodInfo) methods.get(i);
            if (info.getName().charAt(0) == '<') {
                continue;
            }
            if (!hasAnnotation(info.getAttribute(AnnotationsAttribute.visibleTag), At.class.getName())) {
                continue;
            }
            Class<?>[] parameters = parameterTypes(controller, info.getDescriptor());
            try {
                Method method = controller.getDeclaredMethod(info.getName(), parameters);
                actions.add(method);
            } catch (NoSuchMethodException e) {
                throw failure(controller.getName(), "action " + info.getName() + " was not found after enhancement", e);
            }
        }
        return actions;
    }

    private static List<String> annotatedFields(ClassFile file, String annotation) {
        List<String> names = new ArrayList<String>();
        List<?> fields = file.getFields();
        for (int i = 0; i < fields.size(); i++) {
            FieldInfo info = (FieldInfo) fields.get(i);
            if (hasAnnotation(info.getAttribute(AnnotationsAttribute.visibleTag), annotation)) {
                names.add(info.getName());
            }
        }
        return names;
    }

    private static boolean hasAnnotation(Object attribute, String annotation) {
        if (!(attribute instanceof AnnotationsAttribute)) {
            return false;
        }
        Annotation[] annotations = ((AnnotationsAttribute) attribute).getAnnotations();
        String slashed = annotation.replace('.', '/');
        for (int i = 0; i < annotations.length; i++) {
            String type = annotations[i].getTypeName();
            if (annotation.equals(type) || slashed.equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static ClassFile classFile(Class<?> controller) {
        String resource = controller.getName().replace('.', '/') + ".class";
        InputStream stream = controller.getClassLoader() == null
                ? ClassLoader.getSystemResourceAsStream(resource)
                : controller.getClassLoader().getResourceAsStream(resource);
        if (stream == null) {
            throw failure(controller.getName(), "class file is required so filter order does not follow reflection");
        }
        DataInputStream data = new DataInputStream(new BufferedInputStream(stream));
        try {
            return new ClassFile(data);
        } catch (IOException e) {
            throw failure(controller.getName(), "class file could not be read", e);
        } finally {
            try {
                data.close();
            } catch (IOException ignored) {
                // The class file is already parsed or the parse exception is the one to keep.
            }
        }
    }

    private static Class<?>[] parameterTypes(Class<?> controller, String descriptor) {
        int start = descriptor.indexOf('(');
        int end = descriptor.indexOf(')');
        if (start < 0 || end < start) {
            throw failure(controller.getName(), "bad method descriptor " + descriptor);
        }
        String body = descriptor.substring(start + 1, end);
        List<Class<?>> types = new ArrayList<Class<?>>();
        int index = 0;
        while (index < body.length()) {
            char c = body.charAt(index);
            if (c == 'L') {
                int semi = body.indexOf(';', index);
                if (semi < 0) {
                    throw failure(controller.getName(), "bad method descriptor " + descriptor);
                }
                String name = body.substring(index + 1, semi).replace('/', '.');
                try {
                    types.add(Class.forName(name, false, controller.getClassLoader()));
                } catch (ClassNotFoundException e) {
                    throw failure(controller.getName(), "parameter type " + name + " was not found", e);
                }
                index = semi + 1;
            } else if (c == '[') {
                throw failure(controller.getName(), "array parameters are not supported on filter actions");
            } else {
                types.add(primitive(controller, c));
                index++;
            }
        }
        return types.toArray(new Class<?>[types.size()]);
    }

    private static Class<?> primitive(Class<?> controller, char c) {
        switch (c) {
            case 'Z':
                return boolean.class;
            case 'B':
                return byte.class;
            case 'C':
                return char.class;
            case 'S':
                return short.class;
            case 'I':
                return int.class;
            case 'J':
                return long.class;
            case 'F':
                return float.class;
            case 'D':
                return double.class;
            case 'V':
                return void.class;
            default:
                throw failure(controller.getName(), "bad primitive descriptor " + c);
        }
    }

    private static String signature(Class<?>[] parameters) {
        StringBuilder builder = new StringBuilder("(");
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(parameters[i].getSimpleName());
        }
        return builder.append(')').toString();
    }

    private static EnhancementFailure failure(String className, String detail) {
        return failure(className, detail, null);
    }

    private static EnhancementFailure failure(String className, String detail, Exception cause) {
        return new EnhancementFailure(
                EnhancementFailure.Category.CONFIGURATION,
                className,
                "controller-filter",
                "filter",
                detail,
                cause);
    }
}
