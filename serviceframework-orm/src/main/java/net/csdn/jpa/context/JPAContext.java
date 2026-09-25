package net.csdn.jpa.context;

import net.csdn.jpa.model.JPQL;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.PersistenceException;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-26
 * Time: 下午9:21
 * 任何一个线程都都会含有一个JPAContext
 */
public class JPAContext {
    private final JPAConfig jpaConfig;
    private EntityManager entityManager;
    private boolean closed;

    protected JPAContext(JPAConfig jpaConfig) {
        this.jpaConfig = jpaConfig;
        EntityManager manager = jpaConfig.newEntityManager();
        entityManager = manager;
        try {
            manager.setFlushMode(FlushModeType.COMMIT);
            manager.getTransaction().begin();
        } catch (RuntimeException e) {
            try {
                close();
            } catch (RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
        jpaConfig.track(this);
    }

    public JPAConfig getJPAConfig() {
        return jpaConfig;
    }

    public boolean isClosed() {
        return closed;
    }

    boolean isUsable() {
        return !closed && entityManager != null && entityManager.isOpen();
    }

    public JPQL jpql() {
        return new JPQL(this);
    }

    public JPQL jpql(String entity) {
        return new JPQL(this, entity);
    }

    public void closeTx(boolean rollback) {
        if (closed) {
            throw new IllegalStateException("JPAContext is closed");
        }
        EntityManager manager = entityManager;
        if (manager == null || !manager.isOpen()) {
            RuntimeException cleanup = finish();
            IllegalStateException closedNow = new IllegalStateException("JPAContext is closed");
            if (cleanup != null) {
                closedNow.addSuppressed(cleanup);
            }
            throw closedNow;
        }
        RuntimeException failure = null;
        try {
            if (manager.getTransaction().isActive()) {
                if (rollback || manager.getTransaction().getRollbackOnly()) {
                    manager.getTransaction().rollback();
                } else {
                    try {
                        manager.getTransaction().commit();
                    } catch (Throwable e) {
                        for (int i = 0; i < 10; i++) {
                            if (e instanceof PersistenceException && e.getCause() != null) {
                                e = e.getCause();
                                break;
                            }
                            e = e.getCause();
                            if (e == null) {
                                break;
                            }
                        }
                        throw new RuntimeException("Cannot commit", e);
                    }
                }
            }
        } catch (RuntimeException e) {
            failure = e;
        }
        RuntimeException closeFailure = null;
        try {
            if (manager.isOpen()) {
                manager.close();
            } else {
                jpaConfig.releaseEntityManager(manager);
            }
        } catch (RuntimeException e) {
            closeFailure = e;
            jpaConfig.releaseEntityManager(manager);
        }
        RuntimeException cleanup = finish();
        if (cleanup != null) {
            if (closeFailure == null) {
                closeFailure = cleanup;
            } else {
                closeFailure.addSuppressed(cleanup);
            }
        }
        if (failure != null && closeFailure != null) {
            failure.addSuppressed(closeFailure);
        } else if (failure == null) {
            failure = closeFailure;
        }
        if (failure != null) {
            throw failure;
        }
    }

    private RuntimeException finish() {
        try {
            jpaConfig.clearJPAContext();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    protected void close() {
        if (closed) {
            return;
        }
        closed = true;
        jpaConfig.untrack(this);
        EntityManager manager = entityManager;
        entityManager = null;
        if (manager == null) {
            return;
        }
        RuntimeException failure = null;
        try {
            if (manager.isOpen() && manager.getTransaction().isActive()) {
                manager.getTransaction().rollback();
            }
        } catch (RuntimeException e) {
            failure = e;
        }
        try {
            releaseJdbc(manager);
        } catch (RuntimeException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        }
        try {
            if (manager.isOpen()) {
                manager.close();
            }
        } catch (RuntimeException e) {
            if (failure == null) {
                failure = e;
            } else {
                failure.addSuppressed(e);
            }
        } finally {
            jpaConfig.releaseEntityManager(manager);
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Return a connection checked out on another thread before the session is closed.
     * {@code Session.disconnect()} does not put that connection back in Hibernate's pool.
     */
    private static void releaseJdbc(EntityManager manager) {
        if (manager == null || !manager.isOpen()) {
            return;
        }
        try {
            org.hibernate.engine.spi.SessionImplementor session =
                    manager.unwrap(org.hibernate.engine.spi.SessionImplementor.class);
            if (session == null) {
                return;
            }
            org.hibernate.resource.jdbc.LogicalConnection connection =
                    session.getJdbcCoordinator().getLogicalConnection();
            if (connection != null && connection.isPhysicallyConnected()) {
                connection.close();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable thrown) {
            throw new IllegalStateException("jdbc connection was not released", thrown);
        }
    }

    public EntityManager em() {
        if (closed || entityManager == null || !entityManager.isOpen()) {
            throw new IllegalStateException("JPAContext is closed");
        }
        return entityManager;
    }

    public void setRollbackOnly() {
        entityManager.getTransaction().setRollbackOnly();
    }

    public int execute(String query) {
        return entityManager.createQuery(query).executeUpdate();
    }

    public boolean isInsideTransaction() {
        return entityManager.getTransaction() != null;
    }
}
