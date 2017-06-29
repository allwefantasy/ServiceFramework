package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.CtNewMethod;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;

import java.lang.reflect.Modifier;
import java.util.List;

import static net.csdn.common.collections.WowCollections.list;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 下午8:48
 */
public class ClassMethodEnhancer implements BitEnhancer {
    private Settings settings;

    public ClassMethodEnhancer(Settings settings) {
        this.settings = settings;
    }

    private List<String> shouldCopyToSubclassStaticMethods = list(
            "validate_info",
            "validate"
    );

    @Override
    public void enhance(List<ModelClass> modelClasses) throws Exception {
        for (ModelClass modelClass : modelClasses) {
            copyStaticFieldsToSubclass(modelClass.originClass);
            copyStaticMethodsToSubclass(modelClass.originClass);
            enhanceModelMethods(modelClass.originClass);
        }

    }

    private void copyStaticMethodsToSubclass(CtClass ctClass) {

        try {
            CtClass parent = ctClass.getSuperclass();
            while (!parent.getName().equals("net.csdn.jpa.model.Model")) {
                if (parent.getName().equals("java.lang.Object")) break;
                parent = parent.getSuperclass();
            }
            copyStaticMethodsToSubclass(parent, ctClass);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void copyStaticFieldsToSubclass(CtClass ctClass) {

        try {
            CtClass parent = ctClass.getSuperclass();
            while (!parent.getName().equals("net.csdn.jpa.model.Model")) {
                if (parent.getName().equals("java.lang.Object")) break;
                parent = parent.getSuperclass();
            }
            copyStaticFieldsToSubclass(parent, ctClass);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void enhanceModelMethods(CtClass ctClass) throws Exception {
        String entityName = ctClass.getName();
        String simpleEntityName = ctClass.getSimpleName();

// count
        CtMethod count = CtMethod.make("public static long count() { return getJPAContext().jpql().count(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(count);

// count2
        CtMethod count2 = CtMethod.make("public static long count(String query, Object[] params) { return  getJPAContext().jpql().count(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(count2);

// findAll
        CtMethod findAll = CtMethod.make("public static java.util.List findAll() { return  getJPAContext().jpql().findAll(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(findAll);

// findById
        CtMethod findById = CtMethod.make("public static net.csdn.jpa.model.JPABase findById(Object id) { return  getJPAContext().jpql().findById(" + entityName + ".class, id); }", ctClass);
        ctClass.addMethod(findById);

// find
        CtMethod find = CtMethod.make("public static net.csdn.jpa.model.Model.JPAQuery find(String query, Object[] params) { return  getJPAContext().jpql().find(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(find);

// find
        CtMethod find2 = CtMethod.make("public static net.csdn.jpa.model.Model.JPAQuery find() { return  getJPAContext().jpql().find(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(find2);

// all
        CtMethod all = CtMethod.make("public static net.csdn.jpa.model.Model.JPAQuery all() { return  getJPAContext().jpql().all(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(all);

// delete
        CtMethod delete = CtMethod.make("public static int delete(String query, Object[] params) { return  getJPAContext().jpql().delete(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(delete);

// deleteAll
        CtMethod deleteAll = CtMethod.make("public static int deleteAll() { return  getJPAContext().jpql().deleteAll(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(deleteAll);

// findOneBy
        CtMethod findOneBy = CtMethod.make("public static net.csdn.jpa.model.JPABase findOneBy(String query, Object[] params) { return  getJPAContext().jpql().findOneBy(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(findOneBy);

// create
        CtMethod create = CtMethod.make("public static net.csdn.jpa.model.JPABase create(java.util.Map params) { return  getJPAContext().jpql().create(" + entityName + ".class, params); }", ctClass);
        ctClass.addMethod(create);

// where
        CtMethod where = CtMethod.make("public static net.csdn.jpa.model.JPQL where(String cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").where(cc);}", ctClass);
        ctClass.addMethod(where);

// in query
        CtMethod in = CtMethod.make("public static net.csdn.jpa.model.JPQL in(String cc,java.util.Map params){return getJPAContext().jpql(\"" + simpleEntityName + "\").in(cc,params);}", ctClass);
        ctClass.addMethod(in);

        CtMethod in2 = CtMethod.make("public static net.csdn.jpa.model.JPQL in(String cc,java.util.List params){return getJPAContext().jpql(\"" + simpleEntityName + "\").in(cc,params);}", ctClass);
        ctClass.addMethod(in2);

// where2
        CtMethod where2 = CtMethod.make("public static net.csdn.jpa.model.JPQL where(String cc,java.util.Map params){return getJPAContext().jpql(\"" + simpleEntityName + "\").where(cc,params);}", ctClass);
        ctClass.addMethod(where2);
// where3
        CtMethod where3 = CtMethod.make("public static net.csdn.jpa.model.JPQL where(java.util.Map params){return getJPAContext().jpql(\"" + simpleEntityName + "\").where(params);}", ctClass);
        ctClass.addMethod(where3);

// select
        CtMethod select = CtMethod.make("public static net.csdn.jpa.model.JPQL select(String cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").select(cc);}", ctClass);
        ctClass.addMethod(select);
// joins
        CtMethod joins = CtMethod.make("public static net.csdn.jpa.model.JPQL joins(String cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").joins(cc);}", ctClass);
        ctClass.addMethod(joins);

// order
        CtMethod order = CtMethod.make("public static net.csdn.jpa.model.JPQL order(String cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").order(cc);}", ctClass);
        ctClass.addMethod(order);
// limit
        CtMethod limit = CtMethod.make("public static net.csdn.jpa.model.JPQL limit(int cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").limit(cc);}", ctClass);
        ctClass.addMethod(limit);
// offset
        CtMethod offset = CtMethod.make("public static net.csdn.jpa.model.JPQL offset(int cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").offset(cc);}", ctClass);
        ctClass.addMethod(offset);

        CtMethod findWithSingleId = CtMethod.make("public static net.csdn.jpa.model.JPABase  find(Integer cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").find(cc);}", ctClass);
        ctClass.addMethod(findWithSingleId);

        CtMethod findWithMultiId = CtMethod.make("public static java.util.List find(java.util.List cc){return getJPAContext().jpql(\"" + simpleEntityName + "\").find(cc);}", ctClass);
        ctClass.addMethod(findWithMultiId);


        ctClass.defrost();

    }


    private void copyStaticMethodsToSubclass(CtClass document, CtClass targetClass) throws Exception {
        CtMethod[] ctMethods = document.getMethods();

        for (CtMethod ctMethod : ctMethods) {
            if (Modifier.isStatic(ctMethod.getModifiers()) && shouldCopyToSubclassStaticMethods.contains(ctMethod.getName())) {
                CtMethod ctNewMethod = CtNewMethod.copy(ctMethod, targetClass, null);
                targetClass.addMethod(ctNewMethod);
            }

        }
    }

    private void copyStaticFieldsToSubclass(CtClass document, CtClass targetClass) throws Exception {
        CtField[] ctFields = document.getFields();
        for (CtField ctField : ctFields) {
            if (Modifier.isStatic(ctField.getModifiers()) && ctField.getName().startsWith("parent$_")) {
                CtField ctField1 = new CtField(ctField.getType(), ctField.getName(), targetClass);
                ctField1.setModifiers(ctField.getModifiers());
                targetClass.addField(ctField1);
            }
        }

    }
}
