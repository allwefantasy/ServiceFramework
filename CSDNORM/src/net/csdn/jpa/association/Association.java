package net.csdn.jpa.association;

import net.csdn.common.reflect.ReflectHelper;
import net.csdn.jpa.JPA;
import net.csdn.jpa.model.JPABase;
import net.csdn.jpa.model.JPQL;
import net.csdn.jpa.model.Model;

import javax.persistence.EntityManager;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: WilliamZhu
 * Date: 12-7-26
 * Time: 下午7:36
 */
public class Association {
    private JPABase object;
    private JPABase targetObject;

    private String field;
    private String targetField;

    private String type;

    //for manyToMany  Association
    private String tableName;
    //check object is master(without mappedBy in ManyToMany)
    private boolean master;

    public Association(Model object, String field, String targetField, String type) {
        this.object = object;
        this.field = field;
        this.targetField = targetField;
        this.type = type;
    }

    public Association(Model object, String field, String targetField, String type, String tableName, String master) {
        this(object, field, targetField, type);
        this.tableName = tableName;
        this.master = Boolean.parseBoolean(master);
    }


    private EntityManager em() {
        return JPA.getJPAConfig().getJPAContext().em();
    }

    public void remove(JPABase model) {
        this.targetObject = model;
        try {
            if (type.equals("javax.persistence.ManyToMany")) {
                String idFiled1 = field + "_id";
                String idFiled2 = targetField + "_id";
                em().createNativeQuery(format("delete from " + tableName + " where {}={} and {}={}", idFiled1, targetObject.id(), idFiled2, object.id())).executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List fetch() {
        return jpql().fetch();
    }


    private Class getTargetModelClass() {
        try {
            Field tempField = object.getClass().getDeclaredField(field);
            Class clzz = tempField.getType();
            if (clzz.getSuperclass() == Model.class) {

            } else {
                clzz = (Class) ((ParameterizedType) tempField.getGenericType()).getActualTypeArguments()[0];
            }
            return clzz;

        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        return null;
    }

    private JPQL jpql() {
        return (JPQL) ReflectHelper.method(getTargetModelClass(), "where", targetField + "=:framework_service_holder", map("framework_service_holder", object));
    }

    public JPQL where(String where, Map<String, Object> params) {
        return jpql().where(where, params);
    }

    public JPQL where(String where) {
        return where(where, map());
    }


    public JPQL order(String orderBy) {
        return jpql().order(orderBy);
    }

    public JPQL offset(Integer offset) {
        return jpql().offset(offset);
    }

    public JPQL limit(Integer limit) {
        return jpql().limit(limit);
    }

    public void delete() {
        if (!master) {
            try {
                EntityManager entityManager = JPA.getJPAConfig().getJPAContext().em();
                if (type.equals("javax.persistence.ManyToMany")) {
                    String idFiled1 = field + "_id";
                    entityManager.createNativeQuery(format("delete from " + tableName + " where  {}={}", idFiled1, object.id())).executeUpdate();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        object.delete();
    }


    public void set(JPABase model) {
        add(model);
    }

    public void add(JPABase model) {
        this.targetObject = model;
        try {
            if (type.equals("javax.persistence.OneToMany")) {
                object.attr(field, Collection.class).add(targetObject);
                targetObject.attr(targetField, object);
            }

            if (type.equals("javax.persistence.OneToOne")) {
                object.attr(field, targetObject);
                targetObject.attr(targetField, object);
            }

            if (type.equals("javax.persistence.ManyToOne")) {
                object.attr(field, targetObject);
                targetObject.attr(targetField, Collection.class).add(object);
            }
            if (type.equals("javax.persistence.ManyToMany")) {
                object.attr(field, Collection.class).add(targetObject);
                targetObject.attr(targetField, Collection.class).add(object);
            }
            //object.save();
            targetObject.save();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
