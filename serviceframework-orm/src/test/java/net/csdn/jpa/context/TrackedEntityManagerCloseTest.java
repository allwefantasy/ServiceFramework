package net.csdn.jpa.context;

import org.hibernate.Session;
import org.junit.Test;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TrackedEntityManagerCloseTest {

    @Test
    public void closeRollsBackThenClosesAndUnregisters() throws Exception {
        State state = new State();
        EntityManager tracked = track(state);
        tracked.close();
        assertTrue(state.rolledBack);
        assertTrue(state.closed);
        assertFalse(state.open);
        assertEquals(0, state.config.openEntityManagerCount());
    }

    @Test
    public void closeFailureStillUnregistersAndKeepsTheRollbackFailure() throws Exception {
        State state = new State();
        state.rollbackFailure = new IllegalStateException("rollback failed");
        state.closeFailure = new IllegalStateException("close failed");
        EntityManager tracked = track(state);
        try {
            tracked.close();
            fail("close failures were ignored");
        } catch (IllegalStateException expected) {
            assertEquals("rollback failed", expected.getMessage());
            assertEquals(1, expected.getSuppressed().length);
            assertEquals("close failed", expected.getSuppressed()[0].getMessage());
        }
        assertTrue(state.closeAttempted);
        assertEquals(0, state.config.openEntityManagerCount());
    }

    @Test
    public void closeFailureAfterRollbackIsPrimary() throws Exception {
        State state = new State();
        state.closeFailure = new IllegalStateException("close failed");
        EntityManager tracked = track(state);
        try {
            tracked.close();
            fail("close failure was ignored");
        } catch (IllegalStateException expected) {
            assertEquals("close failed", expected.getMessage());
            assertEquals(0, expected.getSuppressed().length);
        }
        assertTrue(state.rolledBack);
        assertEquals(0, state.config.openEntityManagerCount());
    }

    private static EntityManager track(State state) throws Exception {
        Constructor<JPAConfig> constructor = JPAConfig.class.getDeclaredConstructor(String.class);
        constructor.setAccessible(true);
        state.config = constructor.newInstance("close-contract");
        Field managers = JPAConfig.class.getDeclaredField("openManagers");
        managers.setAccessible(true);
        OpenEntityManagers open = (OpenEntityManagers) managers.get(state.config);
        Session delegate = (Session) Proxy.newProxyInstance(
                Session.class.getClassLoader(),
                new Class<?>[]{Session.class},
                state);
        EntityManager tracked = open.track(state.config, delegate);
        assertEquals(1, state.config.openEntityManagerCount());
        return tracked;
    }

    private static final class State implements InvocationHandler {
        private JPAConfig config;
        private boolean open = true;
        private boolean active = true;
        private boolean rolledBack;
        private boolean closed;
        private boolean closeAttempted;
        private RuntimeException rollbackFailure;
        private RuntimeException closeFailure;

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            if ("isOpen".equals(name)) {
                return Boolean.valueOf(open);
            }
            if ("getTransaction".equals(name)) {
                return transaction();
            }
            if ("close".equals(name)) {
                closeAttempted = true;
                closed = true;
                open = false;
                if (closeFailure != null) {
                    throw closeFailure;
                }
                return null;
            }
            if ("isActive".equals(name)) {
                return Boolean.valueOf(active);
            }
            if ("rollback".equals(name)) {
                if (rollbackFailure != null) {
                    throw rollbackFailure;
                }
                rolledBack = true;
                active = false;
                return null;
            }
            Class<?> type = method.getReturnType();
            if (type == boolean.class) {
                return Boolean.FALSE;
            }
            if (type == int.class) {
                return Integer.valueOf(0);
            }
            return null;
        }

        private EntityTransaction transaction() {
            final State state = this;
            return (EntityTransaction) Proxy.newProxyInstance(
                    EntityTransaction.class.getClassLoader(),
                    new Class<?>[]{EntityTransaction.class},
                    new InvocationHandler() {
                        @Override
                        public Object invoke(Object proxy, Method method, Object[] args) {
                            return state.invoke(proxy, method, args);
                        }
                    });
        }
    }
}
