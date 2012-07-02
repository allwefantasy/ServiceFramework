package net.csdn.jpa.context;

import net.csdn.jpa.model.JPQL;

import javax.persistence.EntityManager;
import javax.persistence.FlushModeType;
import javax.persistence.PersistenceException;

/**
 * User: WilliamZhu
 * Date: 12-6-26
 * Time: 下午9:21
 * 任何一个线程都都会含有一个JPAContext
 */
public class JPAContext {
    private JPAConfig jpaConfig;
    private EntityManager entityManager;

    protected JPAContext(JPAConfig jpaConfig) {

        this.jpaConfig = jpaConfig;

        EntityManager manager = jpaConfig.newEntityManager();
        manager.setFlushMode(FlushModeType.COMMIT);
        //manager.setProperty("org.hibernate.readOnly", readonly);
        //默认都提供事物支持
        manager.getTransaction().begin();
        entityManager = manager;
    }

    public JPAConfig getJPAConfig() {
        return jpaConfig;
    }

    public JPQL jpql() {
        return new JPQL(this);
    }

    public JPQL jpql(String entity) {
        return new JPQL(this, entity);
    }

    public void closeTx(boolean rollback) {

        try {
            if (entityManager.getTransaction().isActive()) {
                if (rollback || entityManager.getTransaction().getRollbackOnly()) {
                    entityManager.getTransaction().rollback();
                } else {
                    try {
                        entityManager.getTransaction().commit();
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
        } finally {
            entityManager.close();
            //clear context
            jpaConfig.clearJPAContext();
        }

    }

    protected void close() {
        entityManager.close();
    }

    public EntityManager em() {
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
