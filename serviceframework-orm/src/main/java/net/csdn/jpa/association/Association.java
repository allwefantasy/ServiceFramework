package net.csdn.jpa.association;

import net.csdn.common.Strings;
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
                String idFiled1 = Strings.toUnderscoreCase(field) + "_id";
                String idFiled2 = Strings.toUnderscoreCase(targetField) + "_id";
                em().createNativeQuery(format("delete from " + tableName + " where {}={} and {}={}", idFiled1, targetObject.id(), idFiled2, object.id())).executeUpdate();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List fetch() {
        return jpql().fetch();
    }

    public long count() {
        return jpql().count_fetch();
    }

    public long count(String countStr) {
        return jpql().count_fetch(countStr);
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

//    private JPQL jpql2() {
//        Session session = JPA.getJPAConfig().getJPAContext().em().unwrap(Session.class);
//        if (type.equals("javax.persistence.ManyToMany") || type.equals("javax.persistence.oneToMany")) {
//            Query query = session.createFilter((Collection) ReflectHelper.method(object, targetField), "");
//            query.
//        }

    private JPQL jpql() {
        JPQL jpql = null;
        if (type.equals("javax.persistence.ManyToMany") || type.equals("javax.persistence.ManyToOne")) {
            jpql = (JPQL) ReflectHelper.staticMethod(getTargetModelClass(), "joins", targetField + " as framework_service_holder_" + targetField);
        }

        if (jpql != null) {
            jpql.where(map("framework_service_holder_" + targetField, object));
        } else {
            jpql = (JPQL) ReflectHelper.staticMethod(getTargetModelClass(), "where", targetField + "=:framework_service_holder", map("framework_service_holder", object));
        }
        return jpql;
    }

    public JPQL where(String where, Map<String, Object> params) {
        return jpql().where(where, params);
    }

    public JPQL where(String where) {
        return where(where, map());
    }

    public JPQL where(Map whereQuery) {
        return jpql().where(whereQuery);
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
                    String idFiled1 = Strings.toUnderscoreCase(field + "_id");
                    entityManager.createNativeQuery(format("delete from " + tableName + " where  {}={}", idFiled1, object.id())).executeUpdate();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        object.delete();
    }


    public boolean set(JPABase model) {
        return add(model);
    }

    public boolean add(Map params) {
        try {
            return add((JPABase) ReflectHelper.staticMethod(getTargetModelClass(), "create", params));
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

    }

    public boolean add(JPABase model) {
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
            return targetObject.save();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}
