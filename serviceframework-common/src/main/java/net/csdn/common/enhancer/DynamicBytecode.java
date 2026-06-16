package net.csdn.common.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;

import java.lang.reflect.Modifier;

/**
 * Shared Javassist helpers for ServiceFramework's convention based runtime model.
 *
 * The methods in this class intentionally stay Java 8 compatible. They centralize
 * bytecode operations that used to be repeated in ORM, Mongo and controller
 * enhancers, and they expose small convention helpers that are easy to test.
 */
public final class DynamicBytecode {

    private DynamicBytecode() {
    }

    public static interface CtFieldFilter {
        boolean accept(CtField field) throws Exception;
    }

    public static interface CtMethodFilter {
        boolean accept(CtMethod method) throws Exception;
    }

    public static interface SetterBody {
        String beforeAssignment(CtField field) throws Exception;
    }

    public static final CtFieldFilter PARENT_STATIC_FIELD_FILTER = new CtFieldFilter() {
        @Override
        public boolean accept(CtField field) {
            return isStatic(field) && field.getName().startsWith("parent$_");
        }
    };

    public static final CtFieldFilter INSTANCE_FIELD_FILTER = new CtFieldFilter() {
        @Override
        public boolean accept(CtField field) {
            return isInstanceDataField(field);
        }
    };

    public static boolean isStatic(CtField field) {
        return Modifier.isStatic(field.getModifiers());
    }

    public static boolean isStatic(CtMethod method) {
        return Modifier.isStatic(method.getModifiers());
    }

    public static boolean isFinal(CtField field) {
        return Modifier.isFinal(field.getModifiers());
    }

    public static boolean isInstanceDataField(CtField field) {
        return !isStatic(field) && !isFinal(field) && field.getName().indexOf('$') == -1;
    }

    public static String capitalizeProperty(String fieldName) {
        if (fieldName == null || fieldName.length() == 0) {
            throw new IllegalArgumentException("fieldName must not be empty");
        }
        return fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    public static String getterName(CtField field) {
        return getterName(field.getName());
    }

    public static String getterName(String fieldName) {
        return "get" + capitalizeProperty(fieldName);
    }

    public static String setterName(CtField field) {
        return setterName(field.getName());
    }

    public static String setterName(String fieldName) {
        return "set" + capitalizeProperty(fieldName);
    }

    public static String jpaQueryToken(String fieldName) {
        return "by" + capitalizeProperty(fieldName);
    }

    public static String finderName(String fieldName) {
        return "findBy" + capitalizeProperty(fieldName);
    }

    public static String findAllName(String fieldName) {
        return "findAllBy" + capitalizeProperty(fieldName);
    }

    public static String whereName(String fieldName) {
        return "whereBy" + capitalizeProperty(fieldName);
    }

    public static String countName(String fieldName) {
        return "countBy" + capitalizeProperty(fieldName);
    }

    public static String deleteName(String fieldName) {
        return "deleteBy" + capitalizeProperty(fieldName);
    }

    public static String javaString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder(value.length() + 16);
        builder.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                default:
                    builder.append(ch);
                    break;
            }
        }
        builder.append('"');
        return builder.toString();
    }

    public static boolean hasDeclaredMethod(CtClass targetClass, String methodName, CtClass[] parameterTypes) {
        try {
            targetClass.getDeclaredMethod(methodName, parameterTypes);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    public static CtMethod addMethodIfMissing(CtClass targetClass, String methodSource) throws Exception {
        CtMethod method = CtMethod.make(methodSource, targetClass);
        if (hasDeclaredMethod(targetClass, method.getName(), method.getParameterTypes())) {
            return null;
        }
        targetClass.addMethod(method);
        return method;
    }

    public static int copyStaticFields(CtClass sourceClass, CtClass targetClass, CtFieldFilter filter) throws Exception {
        int copied = 0;
        CtField[] fields = sourceClass.getFields();
        for (int i = 0; i < fields.length; i++) {
            CtField field = fields[i];
            if (!isStatic(field) || (filter != null && !filter.accept(field))) {
                continue;
            }
            if (hasDeclaredField(targetClass, field.getName())) {
                continue;
            }
            CtField newField = new CtField(field.getType(), field.getName(), targetClass);
            newField.setModifiers(field.getModifiers());
            newField.getFieldInfo().getAttributes().addAll(field.getFieldInfo().getAttributes());
            targetClass.addField(newField);
            copied++;
        }
        return copied;
    }

    public static int copyStaticMethods(CtClass sourceClass, CtClass targetClass, CtMethodFilter filter) throws Exception {
        int copied = 0;
        CtMethod[] methods = sourceClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
            CtMethod method = methods[i];
            if (!isStatic(method) || (filter != null && !filter.accept(method))) {
                continue;
            }
            if (hasDeclaredMethod(targetClass, method.getName(), method.getParameterTypes())) {
                continue;
            }
            targetClass.addMethod(CtNewMethod.copy(method, targetClass, null));
            copied++;
        }
        return copied;
    }

    public static int addBeanAccessors(CtClass targetClass, CtFieldFilter fieldFilter) throws Exception {
        return addBeanAccessors(targetClass, fieldFilter, null, false);
    }

    public static int addBeanAccessors(CtClass targetClass, CtFieldFilter fieldFilter, SetterBody setterBody, boolean replaceSetter) throws Exception {
        if (targetClass.isFrozen()) {
            targetClass.defrost();
        }
        int added = 0;
        CtField[] fields = targetClass.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            CtField field = fields[i];
            if (fieldFilter != null && !fieldFilter.accept(field)) {
                continue;
            }
            added += addGetterIfMissing(targetClass, field);
            added += addSetter(targetClass, field, setterBody, replaceSetter);
        }
        return added;
    }

    public static int addJpaDynamicFinders(CtClass targetClass, CtFieldFilter fieldFilter) throws Exception {
        int added = 0;
        CtField[] fields = targetClass.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            CtField field = fields[i];
            if (fieldFilter != null && !fieldFilter.accept(field)) {
                continue;
            }
            added += addJpaDynamicFinders(targetClass, field);
        }
        return added;
    }

    public static int addMongoDynamicFinders(CtClass targetClass, CtFieldFilter fieldFilter) throws Exception {
        int added = 0;
        CtField[] fields = targetClass.getDeclaredFields();
        for (int i = 0; i < fields.length; i++) {
            CtField field = fields[i];
            if (fieldFilter != null && !fieldFilter.accept(field)) {
                continue;
            }
            added += addMongoDynamicFinders(targetClass, field);
        }
        return added;
    }

    private static int addJpaDynamicFinders(CtClass targetClass, CtField field) throws Exception {
        String entityName = targetClass.getName();
        String fieldName = field.getName();
        String queryToken = jpaQueryToken(fieldName);
        int added = 0;
        added += methodAdded(addMethodIfMissing(targetClass,
                "public static net.csdn.jpa.model.JPABase " + finderName(fieldName) + "(Object value) {" +
                        "return getJPAContext().jpql().findOneBy(" + javaString(entityName) + "," + javaString(queryToken) + ",new Object[]{value});" +
                        "}"));
        added += methodAdded(addMethodIfMissing(targetClass,
                "public static java.util.List " + findAllName(fieldName) + "(Object value) {" +
                        "return getJPAContext().jpql().findBy(" + javaString(entityName) + "," + javaString(queryToken) + ",new Object[]{value});" +
                        "}"));
        added += methodAdded(addMethodIfMissing(targetClass,
                "public static net.csdn.jpa.model.Model.JPAQuery " + whereName(fieldName) + "(Object value) {" +
                        "return getJPAContext().jpql().find(" + javaString(entityName) + "," + javaString(queryToken) + ",new Object[]{value});" +
                        "}"));
        added += methodAdded(addMethodIfMissing(targetClass,
                "public static long " + countName(fieldName) + "(Object value) {" +
                        "return getJPAContext().jpql().count(" + javaString(entityName) + "," + javaString(queryToken) + ",new Object[]{value});" +
                        "}"));
        added += methodAdded(addMethodIfMissing(targetClass,
                "public static int " + deleteName(fieldName) + "(Object value) {" +
                        "return getJPAContext().jpql().delete(" + javaString(entityName) + "," + javaString(fieldName) + ",new Object[]{value});" +
                        "}"));
        return added;
    }

    private static int addMongoDynamicFinders(CtClass targetClass, CtField field) throws Exception {
        String entityName = targetClass.getName();
        String fieldName = field.getName();
        String mapSetup = "java.util.Map params = new java.util.HashMap(); params.put(" + javaString(fieldName) + ",value);";
        int added = 0;
        added += methodAdded(addMethodIfMissing(targetClass,
                "public static Object " + finderName(fieldName) + "(Object value) {" +
                        mapSetup +
                        "return new net.csdn.mongo.Criteria(" + entityName + ".class).where(params).singleFetch();" +
                        "}"));
        added += methodAdded(addMethodIfMissing(targetClass,
                "public static java.util.List " + findAllName(fieldName) + "(Object value) {" +
                        mapSetup +
                        "return new net.csdn.mongo.Criteria(" + entityName + ".class).where(params).fetch();" +
                        "}"));
        added += methodAdded(addMethodIfMissing(targetClass,
                "public static net.csdn.mongo.Criteria " + whereName(fieldName) + "(Object value) {" +
                        mapSetup +
                        "return new net.csdn.mongo.Criteria(" + entityName + ".class).where(params);" +
                        "}"));
        added += methodAdded(addMethodIfMissing(targetClass,
                "public static int " + countName(fieldName) + "(Object value) {" +
                        mapSetup +
                        "return new net.csdn.mongo.Criteria(" + entityName + ".class).where(params).count();" +
                        "}"));
        return added;
    }

    private static int addGetterIfMissing(CtClass targetClass, CtField field) throws Exception {
        String getterName = getterName(field);
        if (hasDeclaredMethod(targetClass, getterName, new CtClass[0])) {
            return 0;
        }
        CtMethod getter = CtMethod.make(
                "public " + field.getType().getName() + " " + getterName + "() { return this." + field.getName() + "; }",
                targetClass);
        getter.setModifiers(getter.getModifiers() | AccessFlag.SYNTHETIC);
        targetClass.addMethod(getter);
        return 1;
    }

    private static int addSetter(CtClass targetClass, CtField field, SetterBody setterBody, boolean replaceSetter) throws Exception {
        String setterName = setterName(field);
        CtClass[] parameterTypes = new CtClass[]{field.getType()};
        if (replaceSetter) {
            removeDeclaredMethods(targetClass, setterName);
        } else if (hasDeclaredMethod(targetClass, setterName, parameterTypes)) {
            return 0;
        }
        String beforeAssignment = setterBody == null ? "" : setterBody.beforeAssignment(field);
        if (beforeAssignment == null) {
            beforeAssignment = "";
        }
        CtMethod setter = CtMethod.make(
                "public void " + setterName + "(" + field.getType().getName() + " value) { " + beforeAssignment + " this." + field.getName() + " = value; }",
                targetClass);
        setter.setModifiers(setter.getModifiers() | AccessFlag.SYNTHETIC);
        targetClass.addMethod(setter);
        return 1;
    }

    private static boolean hasDeclaredField(CtClass targetClass, String fieldName) {
        try {
            targetClass.getDeclaredField(fieldName);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private static void removeDeclaredMethods(CtClass targetClass, String methodName) throws NotFoundException {
        CtMethod[] methods = targetClass.getDeclaredMethods(methodName);
        for (int i = 0; i < methods.length; i++) {
            targetClass.removeMethod(methods[i]);
        }
    }

    private static int methodAdded(CtMethod method) {
        return method == null ? 0 : 1;
    }
}
