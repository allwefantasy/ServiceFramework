package net.csdn.jpa.context;

import org.hibernate.Session;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Public {@link EntityManager} handle. {@code close()} drops the live
 * registration and closes the Hibernate manager. {@code unwrap(Session.class)}
 * stays on this handle so a cast or unwrap still closes through the same path.
 * Unwrapping a Hibernate implementation class returns the raw manager and is
 * outside this contract.
 *
 * <p>An active transaction is rolled back before {@code close()}. Hibernate's
 * JPA close leaves the session waiting for auto-close while the transaction
 * is still open, and that path does not return the JDBC connection.
 */
final class TrackedEntityManager implements InvocationHandler {

    private final JPAConfig config;
    private final EntityManager delegate;

    private TrackedEntityManager(JPAConfig config, EntityManager delegate) {
        this.config = config;
        this.delegate = delegate;
    }

    static EntityManager wrap(JPAConfig config, EntityManager delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("entity manager is required");
        }
        Class<?>[] interfaces = delegate instanceof Session
                ? new Class<?>[]{EntityManager.class, Session.class}
                : new Class<?>[]{EntityManager.class};
        TrackedEntityManager handler = new TrackedEntityManager(config, delegate);
        return (EntityManager) Proxy.newProxyInstance(
                JPAConfig.class.getClassLoader(), interfaces, handler);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            if ("equals".equals(method.getName())) {
                return Boolean.valueOf(proxy == args[0]);
            }
            if ("hashCode".equals(method.getName())) {
                return Integer.valueOf(System.identityHashCode(proxy));
            }
            if ("toString".equals(method.getName())) {
                return "TrackedEntityManager[" + delegate + "]";
            }
        }
        if ("close".equals(method.getName()) && (args == null || args.length == 0)) {
            closeDelegate((EntityManager) proxy);
            return null;
        }
        if ("unwrap".equals(method.getName()) && args != null && args.length == 1 && args[0] instanceof Class) {
            Class<?> type = (Class<?>) args[0];
            if (type.isInstance(proxy)) {
                return proxy;
            }
        }
        try {
            return method.invoke(delegate, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw e;
        }
    }

    private void closeDelegate(EntityManager proxy) {
        RuntimeException failure = null;
        try {
            rollbackActive();
        } catch (RuntimeException thrown) {
            failure = thrown;
        }
        try {
            if (delegate.isOpen()) {
                delegate.close();
            }
        } catch (RuntimeException thrown) {
            failure = attach(failure, thrown);
        } finally {
            try {
                config.releaseEntityManager(proxy);
            } catch (RuntimeException thrown) {
                failure = attach(failure, thrown);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Finish the transaction first. {@code Session.close()} while it is still
     * active only marks the session for auto-close and keeps the connection.
     */
    private void rollbackActive() {
        if (!delegate.isOpen()) {
            return;
        }
        EntityTransaction transaction = delegate.getTransaction();
        if (transaction != null && transaction.isActive()) {
            transaction.rollback();
        }
    }

    private static RuntimeException attach(RuntimeException primary, RuntimeException next) {
        if (next == null) {
            return primary;
        }
        if (primary == null) {
            return next;
        }
        primary.addSuppressed(next);
        return primary;
    }
}
