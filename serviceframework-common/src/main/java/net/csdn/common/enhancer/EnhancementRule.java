package net.csdn.common.enhancer;

import javassist.CtClass;

import java.util.Collections;
import java.util.List;

/**
 * A rule mutates a {@link CtClass} only. It must not define the JVM class.
 * <p>
 * {@link #requires()} and {@link #before()} name other rule ids. The default
 * implementation has no dependencies and {@link #version()} 1.
 */
public interface EnhancementRule {

    String id();

    boolean matches(CtClass type, EnhancementContext context);

    void apply(CtClass type, EnhancementContext context);

    default int version() {
        return 1;
    }

    default List<String> requires() {
        return Collections.emptyList();
    }

    default List<String> before() {
        return Collections.emptyList();
    }

    /**
     * Classes {@link #apply} will mutate when it is invoked on {@code type}.
     * The default is only {@code type}. A rule that edits other classes, such as
     * ORM entity mapping editing the whole model tree from the first match, must
     * return every class it will edit. The plan freezes original digests for
     * that set before {@link #apply} and does not require this method for a
     * single-class rule.
     * <p>
     * This method must not mutate any class. It is not called when diagnostics
     * are disabled and the context has no observers.
     */
    default List<CtClass> affectedClasses(CtClass type, EnhancementContext context) {
        return Collections.singletonList(type);
    }

    /**
     * Why {@link #matches} returned true. Not called when diagnostics are disabled.
     */
    default String applyReason(CtClass type, EnhancementContext context) {
        return "matched";
    }

    /**
     * Why {@link #matches} returned false. Not called when diagnostics are disabled.
     */
    default String skipReason(CtClass type, EnhancementContext context) {
        return "not matched";
    }
}
