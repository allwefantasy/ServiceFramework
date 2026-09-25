package net.csdn.modules.http;

import net.csdn.common.exception.RenderFinish;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * One around filter. {@link #getNext()} returns the next node, not this node.
 * The last node's action swallows {@link RenderFinish} so code after
 * {@code next.invoke()} still runs. Every other throwable propagates to that
 * around method and then to the caller. This class does not render or log
 * the throwable away.
 */
public class WowAroundFilter {
    private WowAroundFilter next;
    private Method currentFilter;
    private Method action;
    private ApplicationController applicationController;

    public WowAroundFilter(Method currentFilter, Method action, ApplicationController applicationController) {
        this.currentFilter = currentFilter;
        this.action = action;
        this.applicationController = applicationController;
    }

    public void invoke() throws Exception {
        if (currentFilter == null) {
            invokeAction();
            return;
        }
        WowAroundFilter following = this.next;
        if (following == null) {
            following = new WowAroundFilter(null, action, applicationController);
        }
        currentFilter.setAccessible(true);
        try {
            currentFilter.invoke(applicationController, following);
        } catch (InvocationTargetException e) {
            throw propagate(e);
        }
    }

    private void invokeAction() throws Exception {
        action.setAccessible(true);
        try {
            action.invoke(applicationController);
        } catch (InvocationTargetException e) {
            Throwable target = root(e);
            if (target instanceof RenderFinish) {
                return;
            }
            throw propagate(e);
        }
    }

    public void setNext(WowAroundFilter next) {
        this.next = next;
    }

    public WowAroundFilter getNext() {
        return next;
    }

    private static Exception propagate(InvocationTargetException thrown) throws Exception {
        Throwable target = root(thrown);
        if (target instanceof Error) {
            throw (Error) target;
        }
        if (target instanceof Exception) {
            throw (Exception) target;
        }
        throw thrown;
    }

    private static Throwable root(Throwable thrown) {
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
}
