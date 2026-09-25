package net.csdn.jpa.query;

import net.csdn.jpa.JPA;

import javax.persistence.EntityManager;

/**
 * Isolated on purpose. {@link GeneratedQueryExecutor} touches this class only
 * from the no-EntityManager overload, so calls that pass an EntityManager never
 * initialize the JPA context.
 */
final class JpaEntityManagers {

    private JpaEntityManagers() {
    }

    static EntityManager current() {
        return JPA.getJPAConfig().getJPAContext().em();
    }
}
