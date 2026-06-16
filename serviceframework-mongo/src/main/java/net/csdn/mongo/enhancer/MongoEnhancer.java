package net.csdn.mongo.enhancer;

import javassist.*;
import net.csdn.common.enhancer.DynamicBytecode;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.logging.support.MessageFormat;
import net.csdn.common.settings.Settings;
import net.csdn.mongo.MongoMongo;
import net.csdn.mongo.annotations.Transient;
import net.csdn.mongo.annotations.Validate;

import java.io.DataInputStream;
import java.lang.reflect.Modifier;
import java.util.List;

import static net.csdn.common.collections.WowCollections.list;
import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: WilliamZhu
 * Date: 12-10-18
 * Time: 上午11:14
 */
public class MongoEnhancer extends Enhancer {

    private Settings settings;
    private CSLogger logger = Loggers.getLogger(MongoEnhancer.class);

    public MongoEnhancer(Settings settings) {
        this.settings = settings;
    }

    private List<String> shouldNotCopyToSubclassStaticMethods = list(
            "where",
            "select",
            "order",
            "skip",
            "limit",
            "count",
            "in",
            "not",
            "notIn",
            "create",
            "create9",
            "findById",
            "find",
            "findAll"
    );

    @Override
    public CtClass enhanceThisClass(DataInputStream dataInputStream) throws Exception {
        CtClass ctClass = classPool.makeClassIfNew(dataInputStream);
        if (!ctClass.subtypeOf(classPool.get("net.csdn.mongo.Document"))) {
            return ctClass;
        }
        CtClass document = ctClass.getSuperclass();


        //copy static fields to subclass.Importance because of inheritance strategy of java
        DynamicBytecode.copyStaticFields(document, ctClass, DynamicBytecode.PARENT_STATIC_FIELD_FILTER);

        //copy static methods to subclass
        DynamicBytecode.copyStaticMethods(document, ctClass, new DynamicBytecode.CtMethodFilter() {
            @Override
            public boolean accept(CtMethod method) {
                return !shouldNotCopyToSubclassStaticMethods.contains(method.getName());
            }
        });

        enhanceCriteriaClassMethods(ctClass);

        //enhance getter/setter methods to put them into attributes field
        enhanceGetterSetterMethods(ctClass);

        //enhance related association
        enhanceAssociationMethods(ctClass);

        //enhance embedded association
        enhanceAssociationEmbedded(ctClass);

        return ctClass;
    }

    private void enhanceGetterSetterMethods(CtClass ctClass) throws Exception {


        //hibernate 可能需要 setter/getter 方法，好吧 我们为它添加这些方法

        DynamicBytecode.addBeanAccessors(ctClass, new DynamicBytecode.CtFieldFilter() {
            @Override
            public boolean accept(CtField field) throws Exception {
                return DynamicBytecode.isInstanceDataField(field)
                        && !field.hasAnnotation(Validate.class)
                        && !field.hasAnnotation(Transient.class);
            }
        }, new DynamicBytecode.SetterBody() {
            @Override
            public String beforeAssignment(CtField field) {
                return MessageFormat.format(
                        "attributes.put({},{});",
                        "translateFromAlias(" + DynamicBytecode.javaString(field.getName()) + ")",
                        "value"
                );
            }
        }, true);

    }


    private void enhanceAssociationEmbedded(CtClass ctClass) throws Exception {
        CtMethod[] modelMethods = ctClass.getMethods();
        for (CtMethod ctMethod : modelMethods) {
            String returnType = ctMethod.getReturnType().getName();
            if (!Modifier.isStatic(ctMethod.getModifiers())
                    && "net.csdn.mongo.embedded.AssociationEmbedded".equals(returnType)
                    ) {

                String name = ctMethod.getName();
                ctMethod.setBody("return ((net.csdn.mongo.embedded.AssociationEmbedded)associationsEmbeddedMetaData().get(\"" + name + "\")).doNotUseMePlease_newMe(this);");
            }
        }
    }

    private void enhanceAssociationMethods(CtClass ctClass) throws Exception {
        CtMethod[] modelMethods = ctClass.getMethods();
        for (CtMethod ctMethod : modelMethods) {
            String returnType = ctMethod.getReturnType().getName();
            if (!Modifier.isStatic(ctMethod.getModifiers())
                    && "net.csdn.mongo.association.Association".equals(returnType)
                    ) {

                String name = ctMethod.getName();
                ctMethod.setBody("return ((net.csdn.mongo.association.Association)associationsMetaData().get(\"" + name + "\")).doNotUseMePlease_newMe(this);");
            }
        }
    }

    private void enhanceCriteriaClassMethods(CtClass ctClass) throws Exception {
        String entityName = ctClass.getName();
        //String simpleEntityName = ctClass.getSimpleName();

        CtMethod findAll = CtMethod.make("public static java.util.List findAll() {" +
                "        return new net.csdn.mongo.Criteria(" + entityName + ".class).findAll();" +
                "    }", ctClass);
        ctClass.addMethod(findAll);

        //create
        CtMethod create = CtMethod.make("public static net.csdn.mongo.Document create(java.util.Map params) { " +
                entityName + " doc = new " + entityName + "();" +
                "doc.attributes.putAll(translateKeyForParams(params));" +
                "doc.copyAllAttributesToPojoFields();" +
                "return doc;}", ctClass);
        ctClass.addMethod(create);

        //create9
        CtMethod create9 = CtMethod.make("public static net.csdn.mongo.Document create9(java.util.Map params) { " +
                entityName + " doc = new " + entityName + "();" +
                "doc.attributes=translateKeyForParams(params);" +
                "doc.copyAllAttributesToPojoFields();" +
                "return doc;}", ctClass);
        ctClass.addMethod(create9);

        String newCriteriaPiece = "new net.csdn.mongo.Criteria(" + entityName + ".class)";
        String returnTypeCriteriaPiece = "net.csdn.mongo.Criteria";

        //where
        CtMethod where = CtMethod.make(format("public static {} {}(java.util.Map params) {" +
                "        return {}.{}(" +
                "params" +
                ");" +
                "}", returnTypeCriteriaPiece, "where", newCriteriaPiece, "where"), ctClass);
        ctClass.addMethod(where);

        //select
        CtMethod select = CtMethod.make("public static net.csdn.mongo.Criteria select(java.util.List params) {" +
                "        return new net.csdn.mongo.Criteria(" + entityName + ".class).select(" +
                "params" +
                ");" +
                "    }", ctClass);
        ctClass.addMethod(select);


        //order
        CtMethod order = CtMethod.make("public static net.csdn.mongo.Criteria order(java.util.Map params) {" +
                "        return new net.csdn.mongo.Criteria(" + entityName + ".class).order(" +
                "params" +
                ");" +
                "    }", ctClass);
        ctClass.addMethod(order);


        //skip
        CtMethod skip = CtMethod.make("public static net.csdn.mongo.Criteria skip(int params) {" +
                "        return new net.csdn.mongo.Criteria(" + entityName + ".class).skip(" +
                "params" +
                ");" +
                "    }", ctClass);
        ctClass.addMethod(skip);

        //limit
        CtMethod limit = CtMethod.make("public static net.csdn.mongo.Criteria limit(int params) {" +
                "        return new net.csdn.mongo.Criteria(" + entityName + ".class).limit(" +
                "params" +
                ");" +
                "    }", ctClass);
        ctClass.addMethod(limit);


        //count
        CtMethod count = CtMethod.make("public static int count() {" +
                "        return new net.csdn.mongo.Criteria(" + entityName + ".class).count(" +
                ");" +
                "    }", ctClass);
        ctClass.addMethod(count);

        //in
        CtMethod in = CtMethod.make("public static net.csdn.mongo.Criteria in(java.util.Map params) {" +
                "        return new net.csdn.mongo.Criteria(" + entityName + ".class).in(" +
                "params" +
                ");" +
                "    }", ctClass);
        ctClass.addMethod(in);


        //not
        CtMethod not = CtMethod.make("public static net.csdn.mongo.Criteria not(java.util.Map params) {" +
                "        return new net.csdn.mongo.Criteria(" + entityName + ".class).not(" +
                "params" +
                ");" +
                "    }", ctClass);
        ctClass.addMethod(not);


        //notIn
        CtMethod notIn = CtMethod.make("public static net.csdn.mongo.Criteria notIn(java.util.Map params) {" +
                "        return new net.csdn.mongo.Criteria(" + entityName + ".class).notIn(" +
                "params" +
                ");" +
                "    }", ctClass);
        ctClass.addMethod(notIn);

        //findById
        CtMethod findById = CtMethod.make("public static Object findById(Object params) {" +
                "        return new net.csdn.mongo.Criteria(" + entityName + ".class).findById(" +
                "params" +
                ");" +
                "    }", ctClass);
        ctClass.addMethod(findById);


        //findMulti
        CtMethod findMulti = CtMethod.make("public static java.util.List find(java.util.List params) {" +
                "        return new net.csdn.mongo.Criteria(" + entityName + ".class).find(" +
                "params" +
                ");" +
                "    }", ctClass);
        ctClass.addMethod(findMulti);

        DynamicBytecode.addMongoDynamicFinders(ctClass, new DynamicBytecode.CtFieldFilter() {
            @Override
            public boolean accept(CtField field) throws Exception {
                return DynamicBytecode.isInstanceDataField(field)
                        && !field.hasAnnotation(Validate.class)
                        && !field.hasAnnotation(Transient.class);
            }
        });

    }

    @Override
    public void enhanceThisClass2(List<CtClass> ctClasses) throws Exception {
        for (CtClass ctClass : ctClasses) {
            logger.info(MongoMongo.getMongoConfiguration().getClassLoader().getClassLoader() + " load " + ctClass.getName());
            ctClass.toClass(MongoMongo.getMongoConfiguration().getClassLoader().getClassLoader(), MongoMongo.getMongoConfiguration().getClassLoader().getProtectionDomain());
        }
    }


}
