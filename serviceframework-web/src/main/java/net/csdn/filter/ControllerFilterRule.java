package net.csdn.filter;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import net.csdn.common.enhancer.DynamicBytecode;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.enhancer.EnhancementRuleIds;

import java.util.HashSet;
import java.util.Set;

/**
 * Copies controller filter metadata onto the concrete controller and retargets
 * static calls so each controller keeps its own {@code parent$_} maps.
 * The rule does not define the class.
 */
public final class ControllerFilterRule implements EnhancementRule {

    private static final String CONTROLLER = "net.csdn.modules.http.ApplicationController";

    @Override
    public String id() {
        return EnhancementRuleIds.CONTROLLER_FILTER;
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public boolean matches(CtClass type, EnhancementContext context) {
        if (type == null || type.isInterface() || javassist.Modifier.isAbstract(type.getModifiers())) {
            return false;
        }
        String name = type.getName();
        if (CONTROLLER.equals(name) || name.endsWith(".ServiceFrameworkPackageAnchor")) {
            return false;
        }
        try {
            return type.subtypeOf(context.classPool().get(CONTROLLER));
        } catch (NotFoundException e) {
            throw failure(name, "ApplicationController is not visible to the target loader", e);
        }
    }

    @Override
    public void apply(CtClass type, EnhancementContext context) {
        try {
            if (type.isFrozen()) {
                type.defrost();
            }
            CtClass controller = context.classPool().get(CONTROLLER);
            DynamicBytecode.copyStaticFields(controller, type, DynamicBytecode.PARENT_STATIC_FIELD_FILTER);
            DynamicBytecode.copyStaticMethods(controller, type, new DynamicBytecode.CtMethodFilter() {
                @Override
                public boolean accept(CtMethod method) {
                    return true;
                }
            });
            retargetStaticCalls(type);
        } catch (EnhancementFailure failure) {
            throw failure;
        } catch (Exception e) {
            throw failure(type.getName(), "controller filter metadata was not copied", e);
        }
    }

    /**
     * javac binds {@code beforeFilter} to {@code ApplicationController}. Field
     * retargeting inside the copied method is not enough: {@code <clinit>} and
     * the copied method's own call to {@code parent$_before_filter_info} would
     * still enter the superclass.
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
                throw failure(target.getName(), "cannot retarget static calls in " + behavior.getName(), e);
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
            if ("<clinit>".equals(name) || "<init>".equals(name) || !declares(target, name, descriptor)) {
                continue;
            }
            int rewritten = pool.addMethodrefInfo(targetClassIndex, name, descriptor);
            iterator.write16bit(rewritten, pos + 1);
        }
    }

    private static boolean declares(CtClass target, String name, String descriptor) throws NotFoundException {
        CtMethod[] methods = target.getDeclaredMethods();
        for (int i = 0; i < methods.length; i++) {
            if (name.equals(methods[i].getName()) && descriptor.equals(methods[i].getSignature())) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> hierarchy(CtClass target) throws NotFoundException {
        Set<String> owners = new HashSet<String>();
        CtClass current = target.getSuperclass();
        while (current != null) {
            owners.add(current.getName());
            current = current.getSuperclass();
        }
        return owners;
    }

    private static EnhancementFailure failure(String className, String detail, Exception cause) {
        return new EnhancementFailure(
                EnhancementFailure.Category.ENHANCEMENT,
                className,
                EnhancementRuleIds.CONTROLLER_FILTER,
                "enhance",
                detail,
                cause);
    }
}
