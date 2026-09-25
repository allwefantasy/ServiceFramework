package net.csdn.jpa.context;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-26
 * Time: 下午9:19
 */
public class JPAConfig {
    private final String configName;
    private EntityManagerFactory entityManagerFactory = null;
    private final ThreadLocal<JPAContext> local = new ThreadLocal<JPAContext>();
    private final List<JPAContext> contexts = Collections.synchronizedList(new ArrayList<JPAContext>());
    private final OpenEntityManagers openManagers = new OpenEntityManagers();
    private volatile boolean shutdownStarted;
    private volatile boolean shutdownCompleted;

    public JPAConfig(Map<String, String> properties, String configName) {
        this.configName = configName;
        entityManagerFactory = Persistence.createEntityManagerFactory(configName, properties);
    }

    /**
     * Boot from classes already registered on {@code loader}. Does not write persistence.xml.
     */
    public static JPAConfig bootstrap(Map<String, String> properties, String configName, java.util.List<Class<?>> managed, ClassLoader loader) {
        JPAConfig config = new JPAConfig(configName);
        try {
            config.entityManagerFactory = HibernateBoot.create(configName, properties, managed, loader);
        } catch (RuntimeException e) {
            try {
                config.shutdown();
            } catch (RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
        return config;
    }

    private JPAConfig(String configName) {
        this.configName = configName;
    }

    public String getConfigName() {
        return configName;
    }

    public void shutdown() {
        if (shutdownCompleted) {
            return;
        }
        shutdownStarted = true;
        List<Throwable> failures = new ArrayList<Throwable>();
        List<JPAContext> contextSnapshot;
        synchronized (contexts) {
            contextSnapshot = new ArrayList<JPAContext>(contexts);
        }
        for (int i = contextSnapshot.size() - 1; i >= 0; i--) {
            try {
                contextSnapshot.get(i).close();
            } catch (Throwable thrown) {
                failures.add(thrown);
            }
        }
        synchronized (contexts) {
            contexts.clear();
        }
        local.remove();
        try {
            openManagers.closeRemaining();
        } catch (Throwable thrown) {
            failures.add(thrown);
        }
        EntityManagerFactory factory = entityManagerFactory;
        entityManagerFactory = null;
        if (factory != null) {
            try {
                factory.close();
            } catch (Throwable thrown) {
                failures.add(thrown);
            }
        }
        shutdownCompleted = true;
        if (!failures.isEmpty()) {
            throw chain(failures);
        }
    }

    protected void close() {
        shutdown();
    }

    /**
     * @return true 如果 entityManagerFactory 已经启动
     */
    public boolean isEnabled() {
        return entityManagerFactory != null && !shutdownStarted;
    }

    public EntityManager newEntityManager() {
        ensureUsable();
        EntityManager created = entityManagerFactory.createEntityManager();
        EntityManager tracked = openManagers.track(this, created);
        if (shutdownStarted) {
            try {
                tracked.close();
            } catch (RuntimeException closeFailure) {
                // The caller still sees the shutdown failure.
            }
            ensureUsable();
        }
        return tracked;
    }

    /**
     * Managers that have not been closed. Closed requests are not retained.
     */
    public int openEntityManagerCount() {
        return openManagers.liveCount();
    }

    public int openContextCount() {
        return contexts.size();
    }

    void releaseEntityManager(EntityManager manager) {
        openManagers.release(manager);
    }

    public JPAContext getJPAContext() {
        ensureUsable();
        JPAContext context = local.get();
        if (context != null && !context.isUsable()) {
            local.remove();
            try {
                context.close();
            } catch (RuntimeException ignored) {
                // A dead context must not stay registered.
            }
            contexts.remove(context);
            context = null;
        }
        if (context == null) {
            context = new JPAContext(this);
            local.set(context);
        }
        return context;
    }

    /*
      @return old jpacontext
     */
    public JPAContext reInitJPAContext() {
        ensureUsable();
        JPAContext oldContext = local.get();
        local.set(new JPAContext(this));
        return oldContext;
    }

    void track(JPAContext context) {
        if (context != null) {
            contexts.add(context);
        }
    }

    void untrack(JPAContext context) {
        if (context != null) {
            contexts.remove(context);
        }
    }

    protected void clearJPAContext() {
        JPAContext context = local.get();
        if (context == null) {
            return;
        }
        local.remove();
        try {
            context.close();
        } finally {
            contexts.remove(context);
        }
    }

    private void ensureUsable() {
        if (shutdownStarted || entityManagerFactory == null) {
            throw new IllegalStateException("JPAConfig '" + configName + "' is shut down");
        }
    }

    private static RuntimeException chain(List<Throwable> failures) {
        Throwable primary = failures.get(0);
        for (int i = 1; i < failures.size(); i++) {
            primary.addSuppressed(failures.get(i));
        }
        if (primary instanceof RuntimeException) {
            return (RuntimeException) primary;
        }
        if (primary instanceof Error) {
            throw (Error) primary;
        }
        return new IllegalStateException("JPAConfig shutdown failed", primary);
    }
}
