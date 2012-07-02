package net.csdn.jpa.context;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-6-26
 * Time: 下午9:19
 */
public class JPAConfig {
    private final String configName;
    private EntityManagerFactory entityManagerFactory = null;
    private ThreadLocal<JPAContext> local = new ThreadLocal<JPAContext>();

    public JPAConfig(Map<String, String> _properties, String _configName) {
        this.configName = _configName;
        entityManagerFactory = Persistence.createEntityManagerFactory(configName, _properties);
    }

    public String getConfigName() {
        return configName;
    }

    protected void close() {
        if (isEnabled()) {
            try {
                entityManagerFactory.close();
            } catch (Exception e) {
                // ignore it - we don't care if it failed..
            }
            entityManagerFactory = null;
        }
    }

    /**
     * @return true 如果 entityManagerFactory 已经启动
     */
    public boolean isEnabled() {
        return entityManagerFactory != null;
    }


    public EntityManager newEntityManager() {
        return entityManagerFactory.createEntityManager();
    }

    public JPAContext getJPAContext() {
        JPAContext context = local.get();
        //因为是本地线程的，所以没必要担心多线程问题
        if (context == null) {
            context = new JPAContext(this);
            local.set(context);
        }
        return context;
    }

    public boolean threadHasJPAContext() {
        return local.get() != null;
    }

    protected void clearJPAContext() {
        JPAContext context = local.get();
        if (context != null) {
            try {
                context.close();
            } catch (Exception e) {
                // Let's it fail
            }
            local.remove();
        }
    }


}
