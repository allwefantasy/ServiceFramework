package net.csdn.modules.http.support;

import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.WowAroundFilter;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;

public class FilterChainTest {
    @Test
    public void runsBeforeAroundActionAfterInDeclarationOrder() throws Exception {
        Sample controller = new Sample();
        chain(controller, "ok", "beforeA", "beforeB", "outer", "inner", "after").invoke(controller, method("ok"));
        Assert.assertEquals(Arrays.asList(
                "beforeA", "beforeB", "outer-in", "inner-in", "action", "inner-out", "outer-out", "after"),
                controller.events);
    }

    @Test
    public void actionFailureSkipsAroundExitAndKeepsTheOriginal() throws Exception {
        Sample controller = new Sample();
        try {
            chain(controller, "boom", "beforeA", "outer", "inner", "after", "afterBoom").invoke(controller, method("boom"));
            Assert.fail("action exception was swallowed");
        } catch (IllegalStateException e) {
            Assert.assertEquals("boom", e.getMessage());
            Assert.assertEquals(1, e.getSuppressed().length);
            Assert.assertEquals("after-failed", e.getSuppressed()[0].getMessage());
        }
        Assert.assertEquals(Arrays.asList(
                "beforeA", "outer-in", "inner-in", "action", "after", "after-boom"), controller.events);
    }

    @Test
    public void beforeFailureStillRunsAfterAndSkipsTheAction() throws Exception {
        Sample controller = new Sample();
        try {
            chain(controller, "ok", "beforeBoom", "outer", "after").invoke(controller, method("ok"));
            Assert.fail("before exception was swallowed");
        } catch (IllegalStateException e) {
            Assert.assertEquals("before-failed", e.getMessage());
        }
        Assert.assertEquals(Arrays.asList("before-failed-mark", "after"), controller.events);
    }

    private static FilterChain chain(Sample controller, String action, String... names) throws Exception {
        java.util.List<Method> before = new java.util.ArrayList<Method>();
        java.util.List<Method> around = new java.util.ArrayList<Method>();
        java.util.List<Method> after = new java.util.ArrayList<Method>();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            if (name.equals("outer") || name.equals("inner")) {
                around.add(method(name));
            } else if (name.startsWith("after")) {
                after.add(method(name));
            } else {
                before.add(method(name));
            }
        }
        return new FilterChain(before, around, after);
    }

    private static Method method(String name) throws Exception {
        Class<?>[] parameters = ("outer".equals(name) || "inner".equals(name))
                ? new Class<?>[]{WowAroundFilter.class}
                : new Class<?>[0];
        return Sample.class.getDeclaredMethod(name, parameters);
    }

    public static class Sample extends ApplicationController {
        final java.util.List<String> events = new CopyOnWriteArrayList<String>();

        public void beforeA() {
            events.add("beforeA");
        }

        public void beforeB() {
            events.add("beforeB");
        }

        public void beforeBoom() {
            events.add("before-failed-mark");
            throw new IllegalStateException("before-failed");
        }

        public void outer(WowAroundFilter next) throws Exception {
            events.add("outer-in");
            next.invoke();
            events.add("outer-out");
        }

        public void inner(WowAroundFilter next) throws Exception {
            events.add("inner-in");
            next.invoke();
            events.add("inner-out");
        }

        public void after() {
            events.add("after");
        }

        public void afterBoom() {
            events.add("after-boom");
            throw new IllegalStateException("after-failed");
        }

        public void ok() {
            events.add("action");
        }

        public void boom() {
            events.add("action");
            throw new IllegalStateException("boom");
        }
    }

}
