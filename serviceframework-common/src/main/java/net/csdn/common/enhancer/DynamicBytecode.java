package net.csdn.common.enhancer;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;

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

    /**
     * Java statements forming a generated setter hook. The snippet reads a local
     * {@code value} holding the final field value; it is not woven by renaming
     * variables inside an existing body. A new setter assigns the field first, then
     * runs the snippet. An exact existing setter keeps its name, modifiers,
     * annotations, throws clause and every other attribute: the snippet is compiled
     * into a private synthetic helper and appended through
     * {@code insertAfter(asFinally=false)}, so it runs only after the original body
     * returned normally and still inside any synchronized monitor. Other overloads
     * are left in place. A second instrumentation of the same setter is a conflict.
     */
    public static interface SetterBody {
        String beforeAssignment(CtField field) throws Exception;
    }

    private static final String SETTER_HOOK = "ServiceFrameworkSetterHook";

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

    /**
     * Copies static field declarations into {@code targetClass} using that class's
     * constant pool, so ConstantValue, Signature and annotations keep their values.
     * Non-constant static initializers and {@code <clinit>} are not copied. A copied
     * {@code parent$_} field therefore starts at the JVM default until a retargeted
     * accessor initializes it for that class alone.
     */
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
            CtField newField = new CtField(field, targetClass);
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
            CtMethod copiedMethod = CtNewMethod.copy(method, targetClass, null);
            retargetParentStaticFields(copiedMethod, method.getDeclaringClass(), targetClass);
            targetClass.addMethod(copiedMethod);
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
        String name = getterName(field);
        CtClass[] params = new CtClass[0];
        if (hasDeclaredMethod(targetClass, name, params)) {
            return 0;
        }
        rejectAccessorConflict(targetClass, field, name, params, field.getType());
        CtMethod getter = CtMethod.make(
                "public " + field.getType().getName() + " " + name + "() { return this." + field.getName() + "; }",
                targetClass);
        getter.setModifiers(getter.getModifiers() | AccessFlag.SYNTHETIC);
        targetClass.addMethod(getter);
        return 1;
    }

    private static int addSetter(CtClass targetClass, CtField field, SetterBody setterBody, boolean replaceSetter) throws Exception {
        String setterName = setterName(field);
        CtClass[] params = new CtClass[]{field.getType()};
        CtMethod existing = findDeclared(targetClass, setterName, params);
        String hook = setterBody == null ? "" : setterBody.beforeAssignment(field);
        if (hook == null) {
            hook = "";
        }
        if (existing != null && !replaceSetter) {
            return 0;
        }
        if (existing != null && hook.trim().length() == 0) {
            return 0;
        }
        if (existing != null) {
            if (hasHook(existing)) {
                throw accessorConflict(targetClass, field, existing, "setter is already instrumented");
            }
            if (isStatic(existing) || !CtClass.voidType.equals(existing.getReturnType())) {
                throw accessorConflict(targetClass, field, existing, "exact setter is static or does not return void");
            }
            return wrapSetter(targetClass, field, existing, hook);
        }
        rejectAccessorConflict(targetClass, field, setterName, params, CtClass.voidType);
        String body = "this." + field.getName() + " = value;";
        if (hook.trim().length() > 0) {
            body = body + " value = this." + field.getName() + "; " + hook;
        }
        CtMethod setter = CtMethod.make(setterSource(field, setterName, body), targetClass);
        setter.setModifiers(setter.getModifiers() | AccessFlag.SYNTHETIC);
        targetClass.addMethod(setter);
        if (hook.trim().length() > 0) {
            markHook(setter);
        }
        return 1;
    }

    /**
     * Keeps the declared setter untouched and appends a private synthetic helper
     * that receives the final field value after the body returned normally.
     * {@code insertAfter(asFinally=false)} skips the helper when the body throws.
     */
    private static int wrapSetter(CtClass targetClass, CtField field, CtMethod existing, String hook) throws Exception {
        if (Modifier.isAbstract(existing.getModifiers()) || Modifier.isNative(existing.getModifiers())) {
            throw accessorConflict(targetClass, field, existing, "exact setter has no body to instrument");
        }
        String helperName = uniqueMethodName(targetClass, existing.getName() + "$sfHook");
        CtMethod helper = CtMethod.make(
                "private void " + helperName + "(" + field.getType().getName() + " value) { " + hook + " }",
                targetClass);
        helper.setModifiers(Modifier.PRIVATE | AccessFlag.SYNTHETIC);
        markHook(helper);
        targetClass.addMethod(helper);
        try {
            existing.insertAfter("this." + helperName + "(this." + field.getName() + ");", false);
        } catch (CannotCompileException e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.ENHANCEMENT,
                    targetClass.getName(),
                    "bean-accessor",
                    "enhance",
                    "cannot instrument " + existing.getName() + existing.getSignature() + " for " + field.getName(),
                    e);
        }
        markHook(existing);
        return 1;
    }

    private static String setterSource(CtField field, String setterName, String body) throws NotFoundException {
        return "public void " + setterName + "(" + field.getType().getName() + " value) { " + body + " }";
    }

    private static void rejectAccessorConflict(
            CtClass targetClass,
            CtField field,
            String methodName,
            CtClass[] params,
            CtClass returnType) throws Exception {
        CtMethod ancestor = findInherited(targetClass, methodName, params);
        if (ancestor == null) {
            return;
        }
        if (isStatic(ancestor)) {
            throw accessorConflict(targetClass, field, ancestor, "inherited method is static");
        }
        if (isFinal(ancestor)) {
            throw accessorConflict(targetClass, field, ancestor,
                    "inherited final method would keep the superclass field instead of the declared field");
        }
        if (!ancestor.getReturnType().getName().equals(returnType.getName())) {
            throw accessorConflict(targetClass, field, ancestor,
                    "inherited return type is " + ancestor.getReturnType().getName());
        }
    }

    private static EnhancementFailure accessorConflict(CtClass targetClass, CtField field, CtMethod method, String reason) {
        String owner = method.getDeclaringClass() == null ? "?" : method.getDeclaringClass().getName();
        return new EnhancementFailure(
                EnhancementFailure.Category.CONFLICT,
                targetClass.getName(),
                "bean-accessor",
                "enhance",
                reason + " for " + field.getName() + " at " + owner + "." + method.getName() + method.getSignature(),
                null);
    }

    private static CtMethod findInherited(CtClass targetClass, String methodName, CtClass[] params) throws Exception {
        CtClass current = targetClass.getSuperclass();
        while (current != null) {
            CtMethod found = declaredVisible(current, targetClass, methodName, params);
            if (found != null) {
                return found;
            }
            current = current.getSuperclass();
        }
        return findOnInterfaces(targetClass.getInterfaces(), targetClass, methodName, params, new java.util.HashSet<String>());
    }

    private static CtMethod findOnInterfaces(
            CtClass[] interfaces,
            CtClass targetClass,
            String methodName,
            CtClass[] params,
            java.util.Set<String> seen) throws Exception {
        if (interfaces == null) {
            return null;
        }
        for (int i = 0; i < interfaces.length; i++) {
            CtClass face = interfaces[i];
            if (face == null || !seen.add(face.getName())) {
                continue;
            }
            CtMethod found = declaredVisible(face, targetClass, methodName, params);
            if (found != null) {
                return found;
            }
            found = findOnInterfaces(face.getInterfaces(), targetClass, methodName, params, seen);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static CtMethod declaredVisible(CtClass owner, CtClass targetClass, String methodName, CtClass[] params) throws Exception {
        CtMethod[] methods = owner.getDeclaredMethods(methodName);
        CtMethod synthetic = null;
        for (int i = 0; i < methods.length; i++) {
            CtMethod method = methods[i];
            if (!sameParameters(method, params) || !visibleTo(method, targetClass)) {
                continue;
            }
            if ((method.getModifiers() & AccessFlag.SYNTHETIC) != 0) {
                if (synthetic == null) {
                    synthetic = method;
                }
                continue;
            }
            return method;
        }
        return synthetic;
    }

    private static boolean sameParameters(CtMethod method, CtClass[] params) throws NotFoundException {
        CtClass[] actual = method.getParameterTypes();
        if (actual.length != params.length) {
            return false;
        }
        for (int i = 0; i < actual.length; i++) {
            if (!actual[i].getName().equals(params[i].getName())) {
                return false;
            }
        }
        return true;
    }

    private static boolean visibleTo(CtMethod method, CtClass targetClass) {
        int modifiers = method.getModifiers();
        if (Modifier.isPrivate(modifiers)) {
            return false;
        }
        if (Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers)) {
            return true;
        }
        return packageName(method.getDeclaringClass()).equals(packageName(targetClass));
    }

    private static String packageName(CtClass type) {
        if (type == null) {
            return "";
        }
        String name = type.getName();
        int dollar = name.indexOf('$');
        if (dollar >= 0) {
            name = name.substring(0, dollar);
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(0, dot);
    }

    private static CtMethod findDeclared(CtClass targetClass, String methodName, CtClass[] params) {
        try {
            return targetClass.getDeclaredMethod(methodName, params);
        } catch (NotFoundException e) {
            return null;
        }
    }

    private static String uniqueMethodName(CtClass targetClass, String base) throws NotFoundException {
        String name = base;
        int suffix = 2;
        while (targetClass.getDeclaredMethods(name).length > 0) {
            name = base + suffix;
            suffix++;
        }
        return name;
    }

    private static boolean hasHook(CtMethod method) {
        return method.getMethodInfo().getAttribute(SETTER_HOOK) != null;
    }

    private static void markHook(CtMethod method) {
        MethodInfo info = method.getMethodInfo();
        if (info.getAttribute(SETTER_HOOK) == null) {
            info.addAttribute(new AttributeInfo(info.getConstPool(), SETTER_HOOK, new byte[0]));
        }
    }

    /**
     * Copied static methods keep their other class references. Only {@code getstatic}
     * and {@code putstatic} of {@code parent$_} fields declared on the target are
     * retargeted, so each subclass lazily initializes its own metadata map.
     */
    private static void retargetParentStaticFields(CtMethod copied, CtClass sourceClass, CtClass targetClass) throws Exception {
        MethodInfo info = copied.getMethodInfo();
        CodeAttribute code = info.getCodeAttribute();
        if (code == null || sourceClass == null) {
            return;
        }
        ConstPool pool = info.getConstPool();
        int targetClassIndex = pool.addClassInfo(targetClass.getName());
        CodeIterator iterator = code.iterator();
        while (iterator.hasNext()) {
            int pos;
            try {
                pos = iterator.next();
            } catch (BadBytecode e) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.ENHANCEMENT,
                        targetClass.getName(),
                        "bean-accessor",
                        "enhance",
                        "cannot retarget " + copied.getName(),
                        e);
            }
            int opcode = iterator.byteAt(pos);
            if (opcode != Opcode.GETSTATIC && opcode != Opcode.PUTSTATIC) {
                continue;
            }
            int index = iterator.u16bitAt(pos + 1);
            String fieldName = pool.getFieldrefName(index);
            if (fieldName == null || !fieldName.startsWith("parent$_") || !hasDeclaredField(targetClass, fieldName)) {
                continue;
            }
            String owner = pool.getFieldrefClassName(index).replace('/', '.');
            if (!owner.equals(sourceClass.getName())) {
                continue;
            }
            int rewritten = pool.addFieldrefInfo(targetClassIndex, fieldName, pool.getFieldrefType(index));
            iterator.write16bit(rewritten, pos + 1);
        }
    }

    private static boolean hasDeclaredField(CtClass targetClass, String fieldName) {
        try {
            targetClass.getDeclaredField(fieldName);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private static boolean isFinal(CtMethod method) {
        return Modifier.isFinal(method.getModifiers());
    }

    private static int methodAdded(CtMethod method) {
        return method == null ? 0 : 1;
    }
}
