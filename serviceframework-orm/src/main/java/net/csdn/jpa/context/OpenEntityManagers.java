package net.csdn.jpa.context;

import javax.persistence.EntityManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Entity managers that are still open. Closed managers are removed by
 * {@link #release(EntityManager)} or {@link #closeRemaining()}; this is not a
 * history of every manager ever created.
 */
public final class OpenEntityManagers {

    private final Set<EntityManager> open = Collections.synchronizedSet(
            Collections.newSetFromMap(new IdentityHashMap<EntityManager, Boolean>()));

    public EntityManager track(JPAConfig config, EntityManager delegate) {
        EntityManager tracked = TrackedEntityManager.wrap(config, delegate);
        register(tracked);
        return tracked;
    }

    public void register(EntityManager manager) {
        if (manager != null) {
            open.add(manager);
        }
    }

    public void release(EntityManager manager) {
        if (manager != null) {
            open.remove(manager);
        }
    }

    public int liveCount() {
        return open.size();
    }

    /**
     * Closes whatever is still registered. A failure does not skip the others.
     * The first failure is thrown and the rest are suppressed. Every manager
     * is dropped from this set even when its close fails.
     */
    public void closeRemaining() {
        List<EntityManager> snapshot;
        synchronized (open) {
            snapshot = new ArrayList<EntityManager>(open);
            open.clear();
        }
        List<Throwable> failures = new ArrayList<Throwable>();
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            EntityManager manager = snapshot.get(i);
            try {
                if (manager != null && manager.isOpen()) {
                    manager.close();
                }
            } catch (Throwable thrown) {
                failures.add(thrown);
            }
        }
        if (!failures.isEmpty()) {
            throw chain(failures);
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
        return new IllegalStateException("entity manager close failed", primary);
    }
}
