package net.csdn.modules.http.support;

import net.csdn.common.exception.RenderFinish;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.WowAroundFilter;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One action's filters. Order is fixed when the chain is built:
 * before in declaration order, around entry in declaration order, the action,
 * around exit in reverse (the code after {@code next.invoke()}), then after.
 * <p>
 * {@link RenderFinish} is normal render control flow. It does not skip around
 * exit or after, and it is not reported as an error. Any other throwable from
 * before, around or the action skips the rest of that phase. Around methods
 * that do not catch still skip their own exit code; that is the method body,
 * not a hidden finally. After always runs, including when before fails.
 * The original throwable is preserved. An after failure is added with
 * {@link Throwable#addSuppressed} when another throwable is already in flight;
 * otherwise it becomes the failure.
 */
public final class FilterChain {

    private final List<Method> before;
    private final List<Method> around;
    private final List<Method> after;

    public FilterChain(List<Method> before, List<Method> around, List<Method> after) {
        this.before = freeze(before);
        this.around = freeze(around);
        this.after = freeze(after);
    }

    public List<Method> before() {
        return before;
    }

    public List<Method> around() {
        return around;
    }

    public List<Method> after() {
        return after;
    }

    public void invoke(ApplicationController controller, Method action) throws Exception {
        Throwable primary = null;
        try {
            for (int i = 0; i < before.size(); i++) {
                invokeFilter(controller, before.get(i));
            }
            if (around.isEmpty()) {
                invokeAction(controller, action);
            } else {
                link(controller, action).invoke();
            }
        } catch (Throwable thrown) {
            primary = unwrap(thrown);
        } finally {
            for (int i = 0; i < after.size(); i++) {
                try {
                    invokeFilter(controller, after.get(i));
                } catch (Throwable thrown) {
                    Throwable failure = unwrap(thrown);
                    if (primary == null) {
                        primary = failure;
                    } else {
                        primary.addSuppressed(failure);
                    }
                }
            }
        }
        if (primary != null) {
            rethrow(primary);
        }
    }

    private WowAroundFilter link(ApplicationController controller, Method action) {
        WowAroundFilter head = null;
        WowAroundFilter previous = null;
        for (int i = 0; i < around.size(); i++) {
            WowAroundFilter node = new WowAroundFilter(around.get(i), action, controller);
            if (head == null) {
                head = node;
            }
            if (previous != null) {
                previous.setNext(node);
            }
            previous = node;
        }
        return head;
    }

    private static void invokeAction(ApplicationController controller, Method action) throws Exception {
        try {
            action.setAccessible(true);
            action.invoke(controller);
        } catch (InvocationTargetException e) {
            Throwable target = unwrap(e);
            if (target instanceof RenderFinish) {
                return;
            }
            rethrow(target);
        } catch (IllegalAccessException e) {
            throw e;
        }
    }

    private static void invokeFilter(ApplicationController controller, Method filter) throws Exception {
        try {
            filter.setAccessible(true);
            filter.invoke(controller);
        } catch (InvocationTargetException e) {
            rethrow(unwrap(e));
        } catch (IllegalAccessException e) {
            throw e;
        }
    }

    private static Throwable unwrap(Throwable thrown) {
        Throwable current = thrown;
        while (current instanceof InvocationTargetException) {
            Throwable target = ((InvocationTargetException) current).getTargetException();
            if (target == null) {
                break;
            }
            current = target;
        }
        return current;
    }

    private static void rethrow(Throwable thrown) throws Exception {
        if (thrown instanceof Error) {
            throw (Error) thrown;
        }
        if (thrown instanceof Exception) {
            throw (Exception) thrown;
        }
        throw new Exception(thrown);
    }

    private static List<Method> freeze(List<Method> methods) {
        if (methods == null || methods.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Method>(methods));
    }
}
