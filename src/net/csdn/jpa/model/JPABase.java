package net.csdn.jpa.model;

import net.csdn.jpa.JPA;
import net.csdn.jpa.context.JPAConfig;
import net.csdn.jpa.context.JPAContext;
import org.apache.commons.beanutils.BeanUtils;

import javax.persistence.EntityManager;

/**
 * User: WilliamZhu
 * Date: 12-6-26
 * Time: 下午9:53
 */
public class JPABase implements Model {
    public static JPAContext getJPAContext() {
        return getJPAConfig().getJPAContext();
    }


    public static JPAConfig getJPAConfig() {
        return JPA.getJPAConfig();
    }

    //强类型 没办法呀
    public <T> T attr(String fieldName, Class<T> clzz) {
        try {
            return clzz.cast(BeanUtils.getProperty(this, fieldName));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    public JPABase attr(String fieldName, Object value) {
        try {
            BeanUtils.setProperty(this, fieldName, value);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public void save() {
        em().persist(this);
        em().flush();
    }

    public EntityManager em() {
        return getJPAContext().em();
    }

    @Override
    public void delete() {
        em().remove(this);
        em().flush();
    }

    @Override
    public void update() {
        em().refresh(this);
        em().flush();
    }

    @Override
    public Object key() {
        return null;
    }

}
