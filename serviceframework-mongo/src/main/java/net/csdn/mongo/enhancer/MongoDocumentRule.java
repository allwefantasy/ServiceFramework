package net.csdn.mongo.enhancer;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import net.csdn.common.enhancer.ClassDefiner;
import net.csdn.common.enhancer.DynamicBytecode;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.common.logging.support.MessageFormat;
import net.csdn.mongo.annotations.Transient;
import net.csdn.mongo.annotations.Validate;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code mongo-document} rule. It edits the {@link CtClass} only.
 * Definition is the caller's job, after the whole plan has been applied.
 * <p>
 * Static calls compiled against {@code Document} are retargeted onto the model.
 * Otherwise every model's {@code <clinit>} would keep writing one shared
 * {@code parent$_} map. Inherited association methods are copied onto the
 * target; the superclass method body is left alone.
 */
public final class MongoDocumentRule implements EnhancementRule {

    private static final Set<String> QUERY_METHODS = new HashSet<String>(Arrays.asList(
            "where",
            "select",
            "order",
            "skip",
            "limit",
            "count",
            "in",
            "not",
            "notIn",
            "create",
            "create9",
            "findById",
            "find",
            "findAll"
    ));

    /**
     * Recorded only when diagnostics are enabled. Exact setters are wrapped;
     * a custom getter or a different overload is absent from the method diff.
     */
    public static final String APPLY_REASON =
            "mongo-document copies static helpers and adds finders; an exact user setter is wrapped, a custom getter or other overload is left unchanged";

    @Override
    public String id() {
        return EnhancementRuleIds.MONGO_DOCUMENT;
    }

    @Override
    public String applyReason(CtClass type, EnhancementContext context) {
        return APPLY_REASON;
    }

    @Override
    public boolean matches(CtClass type, EnhancementContext context) {
        if (type == null || type.isInterface() || type.isAnnotation() || type.isEnum() || type.isArray()) {
            return false;
        }
        String name = type.getName();
        if ("net.csdn.mongo.Document".equals(name) || ClassDefiner.ANCHOR_SIMPLE_NAME.equals(simpleName(name))) {
            return false;
        }
        try {
            CtClass document = type.getClassPool().get("net.csdn.mongo.Document");
            return type.subtypeOf(document);
        } catch (NotFoundException e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.ENHANCEMENT,
                    name,
                    id(),
                    "match",
                    "Document is not visible to the target loader",
                    e);
        }
    }

    @Override
    public void apply(CtClass type, EnhancementContext context) {
        try {
            enhance(type);
        } catch (EnhancementFailure failure) {
            throw failure;
        } catch (Exception e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.ENHANCEMENT,
                    type == null ? null : type.getName(),
                    id(),
                    "enhance",
                    e.getMessage() == null ? e.getClass().getName() : e.getMessage(),
                    e);
        }
    }

    private void enhance(CtClass ctClass) throws Exception {
        CtClass document = ctClass.getSuperclass();
        DynamicBytecode.copyStaticFields(document, ctClass, DynamicBytecode.PARENT_STATIC_FIELD_FILTER);
        DynamicBytecode.copyStaticMethods(document, ctClass, new DynamicBytecode.CtMethodFilter() {
            @Override
            public boolean accept(CtMethod method) {
                String name = method.getName();
                return !QUERY_METHODS.contains(name)
                        && !"copyInheritedAssociationMetadata".equals(name)
                        && !"verifyAssociationAccessors".equals(name);
            }
        });
        retargetStaticCalls(ctClass);
        enhanceCriteria(ctClass);
        enhanceAccessors(ctClass);
        enhanceAssociations(ctClass, "net.csdn.mongo.association.Association", "associationsMetaData");
        enhanceAssociations(ctClass, "net.csdn.mongo.embedded.AssociationEmbedded", "associationsEmbeddedMetaData");
        scheduleAssociationMetadata(ctClass);
    }

    private void enhanceAccessors(CtClass ctClass) throws Exception {
        DynamicBytecode.addBeanAccessors(ctClass, new DynamicBytecode.CtFieldFilter() {
            @Override
            public boolean accept(CtField field) throws Exception {
                return DynamicBytecode.isInstanceDataField(field)
                        && !field.hasAnnotation(Validate.class)
                        && !field.hasAnnotation(Transient.class);
            }
        }, new DynamicBytecode.SetterBody() {
            @Override
            public String beforeAssignment(CtField field) throws Exception {
                return MessageFormat.format(
                        "attributes.put({},{});",
                        "translateFromAlias(" + DynamicBytecode.javaString(field.getName()) + ")",
                        boxedValue(field));
            }
        }, true);
    }

    private void enhanceAssociations(CtClass ctClass, String returnTypeName, String metadataMethod) throws Exception {
        CtMethod[] methods = ctClass.getMethods();
        for (int i = 0; i < methods.length; i++) {
            CtMethod method = methods[i];
            if (!isAssociationAccessor(method, returnTypeName)) {
                continue;
            }
            CtMethod target = declared(ctClass, method);
            if (target == null) {
                target = copyOntoTarget(ctClass, method);
            }
            String name = method.getName();
            target.setBody("return ((" + returnTypeName + ")" + metadataMethod + "().get(\"" + name + "\")).doNotUseMePlease_newMe(this);");
        }
    }

    private static boolean isAssociationAccessor(CtMethod method, String returnTypeName) throws Exception {
        int modifiers = method.getModifiers();
        if (Modifier.isStatic(modifiers) || method.getParameterTypes().length != 0) {
            return false;
        }
        if ((modifiers & javassist.bytecode.AccessFlag.SYNTHETIC) != 0
                || (modifiers & javassist.bytecode.AccessFlag.BRIDGE) != 0) {
            return false;
        }
        if (!returnTypeName.equals(method.getReturnType().getName())) {
            return false;
        }
        String declaring = method.getDeclaringClass().getName();
        return !"net.csdn.mongo.Document".equals(declaring) && !"java.lang.Object".equals(declaring);
    }

    private static CtMethod declared(CtClass ctClass, CtMethod method) throws NotFoundException {
        try {
            return ctClass.getDeclaredMethod(method.getName(), method.getParameterTypes());
        } catch (NotFoundException e) {
            return null;
        }
    }

    private CtMethod copyOntoTarget(CtClass ctClass, CtMethod method) throws Exception {
        int modifiers = method.getModifiers();
        String declaredOn = method.getDeclaringClass().getName();
        if (Modifier.isFinal(modifiers) || Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers)) {
            String kind = Modifier.isFinal(modifiers) ? "final" : Modifier.isPrivate(modifiers) ? "private" : "static";
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFLICT,
                    ctClass.getName(),
                    id(),
                    "enhance",
                    "cannot override inherited " + kind + " " + method.getName()
                            + " declared on " + declaredOn,
                    null);
        }
        CtMethod copy = CtNewMethod.copy(method, ctClass, null);
        ctClass.addMethod(copy);
        return copy;
    }

    /**
     * Parent metadata is copied into this class's own maps before its static
     * block adds or replaces names. After that block, an accessor with no
     * metadata fails here instead of on the first call.
     */
    private static void scheduleAssociationMetadata(CtClass ctClass) throws Exception {
        javassist.CtConstructor clinit = ctClass.getClassInitializer();
        if (clinit == null) {
            clinit = ctClass.makeClassInitializer();
        }
        String type = ctClass.getName();
        clinit.insertBefore("net.csdn.mongo.Document.copyInheritedAssociationMetadata(" + type + ".class);");
        clinit.insertAfter("net.csdn.mongo.Document.verifyAssociationAccessors(" + type + ".class);");
    }

    private void enhanceCriteria(CtClass ctClass) throws Exception {
        String entityName = ctClass.getName();
        String newCriteria = "new net.csdn.mongo.Criteria(" + entityName + ".class)";
        ctClass.addMethod(CtMethod.make(
                "public static java.util.List findAll() { return " + newCriteria + ".findAll(); }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static net.csdn.mongo.Document create(java.util.Map params) { "
                        + entityName + " doc = new " + entityName + "();"
                        + "doc.attributes.putAll(translateKeyForParams(params));"
                        + "doc.copyAllAttributesToPojoFields();"
                        + "return doc; }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static net.csdn.mongo.Document create9(java.util.Map params) { "
                        + entityName + " doc = new " + entityName + "();"
                        + "doc.attributes=translateKeyForParams(params);"
                        + "doc.copyAllAttributesToPojoFields();"
                        + "return doc; }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static net.csdn.mongo.Criteria where(java.util.Map params) { return " + newCriteria + ".where(params); }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static net.csdn.mongo.Criteria select(java.util.List params) { return " + newCriteria + ".select(params); }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static net.csdn.mongo.Criteria order(java.util.Map params) { return " + newCriteria + ".order(params); }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static net.csdn.mongo.Criteria skip(int params) { return " + newCriteria + ".skip(params); }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static net.csdn.mongo.Criteria limit(int params) { return " + newCriteria + ".limit(params); }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static int count() { return " + newCriteria + ".count(); }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static net.csdn.mongo.Criteria in(java.util.Map params) { return " + newCriteria + ".in(params); }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static net.csdn.mongo.Criteria not(java.util.Map params) { return " + newCriteria + ".not(params); }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static net.csdn.mongo.Criteria notIn(java.util.Map params) { return " + newCriteria + ".notIn(params); }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static Object findById(Object params) { return " + newCriteria + ".findById(params); }",
                ctClass));
        ctClass.addMethod(CtMethod.make(
                "public static java.util.List find(java.util.List params) { return " + newCriteria + ".find(params); }",
                ctClass));
        DynamicBytecode.addMongoDynamicFinders(ctClass, new DynamicBytecode.CtFieldFilter() {
            @Override
            public boolean accept(CtField field) throws Exception {
                return DynamicBytecode.isInstanceDataField(field)
                        && !field.hasAnnotation(Validate.class)
                        && !field.hasAnnotation(Transient.class);
            }
        });
    }

    /**
     * Point static calls that javac bound to Document (or another model) at this
     * class when it has its own copy. Field retargeting inside the copied method
     * is not enough: the call site in {@code <clinit>} would still enter Document.
     */
    private static void retargetStaticCalls(CtClass target) throws Exception {
        Set<String> owners = hierarchy(target);
        CtBehavior[] behaviors = target.getDeclaredBehaviors();
        for (int i = 0; i < behaviors.length; i++) {
            retargetStaticCalls(behaviors[i], target, owners);
        }
    }

    private static void retargetStaticCalls(CtBehavior behavior, CtClass target, Set<String> owners) throws Exception {
        MethodInfo info = behavior.getMethodInfo();
        CodeAttribute code = info.getCodeAttribute();
        if (code == null) {
            return;
        }
        ConstPool pool = info.getConstPool();
        int targetClassIndex = pool.addClassInfo(target.getName());
        CodeIterator iterator = code.iterator();
        while (iterator.hasNext()) {
            int pos;
            try {
                pos = iterator.next();
            } catch (BadBytecode e) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.ENHANCEMENT,
                        target.getName(),
                        EnhancementRuleIds.MONGO_DOCUMENT,
                        "enhance",
                        "cannot retarget static calls in " + behavior.getName(),
                        e);
            }
            if (iterator.byteAt(pos) != Opcode.INVOKESTATIC) {
                continue;
            }
            int index = iterator.u16bitAt(pos + 1);
            String owner = pool.getMethodrefClassName(index);
            if (owner.indexOf('/') >= 0) {
                owner = owner.replace('/', '.');
            }
            if (!owners.contains(owner) || owner.equals(target.getName())) {
                continue;
            }
            String name = pool.getMethodrefName(index);
            String descriptor = pool.getMethodrefType(index);
            if (!declares(target, name, descriptor)) {
                continue;
            }
            int rewritten = pool.addMethodrefInfo(targetClassIndex, name, descriptor);
            iterator.write16bit(rewritten, pos + 1);
        }
    }

    private static boolean declares(CtClass target, String name, String descriptor) throws NotFoundException {
        if ("<clinit>".equals(name) || "<init>".equals(name)) {
            return false;
        }
        CtMethod[] methods = target.getDeclaredMethods(name);
        for (int i = 0; i < methods.length; i++) {
            if (descriptor.equals(methods[i].getSignature())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> hierarchy(CtClass type) throws NotFoundException {
        Set<String> names = new HashSet<String>();
        CtClass current = type;
        while (current != null) {
            names.add(current.getName());
            if ("java.lang.Object".equals(current.getName())) {
                break;
            }
            current = current.getSuperclass();
        }
        return names;
    }

    private static String boxedValue(CtField field) throws Exception {
        CtClass type = field.getType();
        if (!type.isPrimitive()) {
            return "value";
        }
        if (CtClass.booleanType.equals(type)) {
            return "Boolean.valueOf(value)";
        }
        if (CtClass.byteType.equals(type)) {
            return "Byte.valueOf(value)";
        }
        if (CtClass.charType.equals(type)) {
            return "Character.valueOf(value)";
        }
        if (CtClass.shortType.equals(type)) {
            return "Short.valueOf(value)";
        }
        if (CtClass.intType.equals(type)) {
            return "Integer.valueOf(value)";
        }
        if (CtClass.longType.equals(type)) {
            return "Long.valueOf(value)";
        }
        if (CtClass.floatType.equals(type)) {
            return "Float.valueOf(value)";
        }
        if (CtClass.doubleType.equals(type)) {
            return "Double.valueOf(value)";
        }
        return "value";
    }

    private static String simpleName(String name) {
        int dot = name.lastIndexOf('.');
        String simple = dot < 0 ? name : name.substring(dot + 1);
        int dollar = simple.lastIndexOf('$');
        return dollar < 0 ? simple : simple.substring(dollar + 1);
    }
}
