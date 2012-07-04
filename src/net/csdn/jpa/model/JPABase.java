package net.csdn.jpa.model;

import net.csdn.jpa.JPA;
import net.csdn.jpa.context.JPAConfig;
import net.csdn.jpa.context.JPAContext;
import net.csdn.validate.ValidateParse;
import net.csdn.validate.ValidateResult;
import net.csdn.validate.impl.*;
import org.apache.commons.beanutils.BeanUtils;

import javax.persistence.EntityManager;
import javax.persistence.Transient;

import java.util.ArrayList;
import java.util.List;

import static net.csdn.common.collections.WowCollections.newArrayList;

/**
 * User: WilliamZhu
 * Date: 12-6-26
 * Time: 下午9:53
 */
public class JPABase implements Model {

    public final static List validateParses = newArrayList();

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

    @Transient
    public final List<ValidateResult> validateResults = new ArrayList<ValidateResult>();

    public boolean valid() {
        for (Object validateParse : validateParses) {
            ((ValidateParse) validateParse).parse(this, this.validateResults);
        }
        if (validateResults.size() > 0) return false;
        return true;
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
