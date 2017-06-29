package net.csdn.jpa.model;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.reflect.ReflectHelper;
import net.csdn.jpa.JPA;
import net.csdn.jpa.association.Association;
import net.csdn.jpa.context.JPAConfig;
import net.csdn.jpa.context.JPAContext;
import net.csdn.modules.persist.mysql.DataSourceManager;
import net.csdn.modules.persist.mysql.MysqlClient;
import net.csdn.validate.ValidateParse;
import net.csdn.validate.ValidateResult;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.MethodUtils;

import javax.persistence.EntityManager;
import javax.persistence.Transient;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.list;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-26
 * Time: 下午9:53
 */

public class JPABase implements GenericModel {

    protected CSLogger logger = Loggers.getLogger(getClass());
    public final static List validateParses = list();

    public final static MysqlClient mysqlClient = new MysqlClient(new DataSourceManager(JPA.settings()), JPA.settings());

    public static JPAContext getJPAContext() {
        return getJPAConfig().getJPAContext();
    }

    public static <T> T findService(Class<T> clz) {
        return JPA.injector().getInstance(clz);
    }


    public Integer id() {
        return attr("id", Integer.class);
    }

    public static JPAConfig getJPAConfig() {
        return JPA.getJPAConfig();
    }

    //强类型 没办法呀
    public <T> T attr(String fieldName, Class<T> clzz) {
        try {
            Field field = (this.getClass().getDeclaredField(fieldName));
            field.setAccessible(true);
            return clzz.cast(field.get(this));
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

    public <T extends JPABase> T m(String methodName, Object... objs) {

        try {
            return (T) MethodUtils.invokeMethod(this, methodName, objs);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Association associate(String obj) {
        return (Association) ReflectHelper.method(this, obj);
    }

    @Override
    public boolean save() {
        if (valid()) {
            em().persist(this);
            em().flush();
            return true;
        } else {
            return false;
        }


    }

    public boolean save(boolean validate) {
        if (validate && valid()) {
            em().persist(this);
            em().flush();
            return true;
        } else {
            return false;
        }


    }

//    public <T extends JPABase> T add(Map params) {
//        ParamBinding paramBinding = new ParamBinding();
//        paramBinding.parse(params);
//        paramBinding.toModel(this);
//        return (T) this;
//    }

    @Transient
    public final List<ValidateResult> validateResults = new ArrayList<ValidateResult>();

    public boolean valid() {
        if (validateResults.size() > 0) return false;
        for (Object validateParse : validateParses) {
            ((ValidateParse) validateParse).parse(this, this.validateResults);
        }
        return validateResults.size() == 0;
    }

    public EntityManager em() {
        return getJPAContext().em();
    }

    @Override
    public boolean refresh() {
        if (valid()) {
            em().refresh(this);
            em().flush();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void delete() {
        em().remove(this);
        em().flush();
    }

    @Override
    public boolean update() {
        if (valid()) {
            em().merge(this);
            em().flush();
            return true;
        } else {
            return false;
        }
    }

    public boolean merge(Map params) {
        try {
            BeanUtils.copyProperties(this, params);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public Object key() {
        return null;
    }

}
