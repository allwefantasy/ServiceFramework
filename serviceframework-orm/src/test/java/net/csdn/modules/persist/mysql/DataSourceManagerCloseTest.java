package net.csdn.modules.persist.mysql;

import com.alibaba.druid.pool.DruidDataSource;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.jpa.JPA;
import net.csdn.jpa.context.OpenEntityManagers;
import org.junit.After;
import org.junit.Test;

import javax.persistence.EntityManager;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DataSourceManagerCloseTest {

    @After
    public void shutdown() {
        JPA.shutdown();
    }

    @Test
    public void closeClosesASharedPoolOnce() {
        DataSourceManager manager = new DataSourceManager(ImmutableSettings.settingsBuilder().build());
        CountingDataSource dataSource = new CountingDataSource();
        manager.trackForClose(dataSource);
        manager.trackForClose(dataSource);
        manager.close();
        assertEquals(1, dataSource.closes);
        // Druid 1.2 does not set closed on a pool that never called init().
        manager.close();
        assertEquals(1, dataSource.closes);
    }

    @Test
    public void closeFailureReleasesEveryManagerAndKeepsSuppressed() {
        OpenEntityManagers tracker = new OpenEntityManagers();
        int[] attempts = new int[]{0};
        for (int i = 0; i < 40; i++) {
            EntityManager finished = entityManager(false, new boolean[]{false}, attempts);
            tracker.register(finished);
            tracker.release(finished);
        }
        assertEquals(0, tracker.liveCount());

        boolean[] closed = new boolean[]{false};
        EntityManager good = entityManager(false, closed, attempts);
        EntityManager firstFailure = entityManager(true, new boolean[]{false}, attempts);
        EntityManager secondFailure = entityManager(true, new boolean[]{false}, attempts);
        tracker.register(good);
        tracker.register(firstFailure);
        tracker.register(secondFailure);
        assertEquals(3, tracker.liveCount());
        try {
            tracker.closeRemaining();
            fail("close failures were ignored");
        } catch (IllegalStateException expected) {
            assertEquals("close failed", expected.getMessage());
            assertEquals(1, expected.getSuppressed().length);
            assertEquals(IllegalStateException.class, expected.getSuppressed()[0].getClass());
            assertEquals("close failed", expected.getSuppressed()[0].getMessage());
        }
        assertTrue(closed[0]);
        assertEquals(3, attempts[0]);
        assertEquals(0, tracker.liveCount());
        tracker.closeRemaining();
        assertEquals(0, tracker.liveCount());
    }

    private static EntityManager entityManager(final boolean failClose, final boolean[] closed, final int[] attempts) {
        return (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[]{EntityManager.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        if ("isOpen".equals(method.getName())) {
                            return Boolean.valueOf(!closed[0]);
                        }
                        if ("close".equals(method.getName())) {
                            attempts[0]++;
                            if (failClose) {
                                throw new IllegalStateException("close failed");
                            }
                            closed[0] = true;
                            return null;
                        }
                        if ("equals".equals(method.getName())) {
                            return Boolean.valueOf(proxy == args[0]);
                        }
                        if ("hashCode".equals(method.getName())) {
                            return Integer.valueOf(System.identityHashCode(proxy));
                        }
                        if ("toString".equals(method.getName())) {
                            return "entity-manager";
                        }
                        throw new UnsupportedOperationException(method.getName());
                    }
                });
    }

    public static final class CountingDataSource extends DruidDataSource {
        int closes;

        @Override
        public void close() {
            closes++;
            super.close();
        }
    }
}
