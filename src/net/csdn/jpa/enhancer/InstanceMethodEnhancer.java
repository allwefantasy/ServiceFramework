package net.csdn.jpa.enhancer;

import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import net.csdn.ServiceFramwork;
import net.csdn.annotation.association.ManyToManyHint;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.AssociatedHelper;
import net.csdn.enhancer.BitEnhancer;
import net.csdn.enhancer.EnhancerHelper;
import net.csdn.jpa.type.DBInfo;

import static net.csdn.common.logging.support.MessageFormat.format;


/**
 * User: WilliamZhu
 * Date: 12-7-4
 * Time: 下午9:08
 */
public class InstanceMethodEnhancer implements BitEnhancer {
    private Settings settings;

    public InstanceMethodEnhancer(Settings settings) {
        this.settings = settings;
    }

    /*
        Hibernate 的关联关系太复杂了。要么你区分控制端和被控制端。要么你必须在使用的时候将两端都设置好关联关系。
        对于mappedBy也是一个无语的设计。为什么我要通过它来区分控制端？
     */
    @Override
    public void enhance(CtClass ctClass) throws Exception {
        CtField[] fields = ctClass.getDeclaredFields();

        CtMethod ctMethod = CtMethod.make("public void executeInNewSession() {\n" +
                "        net.csdn.jpa.context.JPAContext jpaContext = new net.csdn.jpa.context.JPAContext(getJPAConfig());\n" +
                ""+
                "        }\n" +
                "    }", ctClass);

        ctClass.addMethod(ctMethod);

        for (CtField ctField : fields) {


            if (EnhancerHelper.hasAnnotation(ctField, "javax.persistence.OneToMany")) {


                String clzzName = findAssociatedClassName(ctField);

                String mappedByFieldName = findAssociatedFieldName(ctClass, clzzName);
                String mappedByClassName = ctClass.getName();

                //如果没有设置mappedBy我们帮他设置吧
                setMappedBy(ctField, mappedByFieldName, "OneToMany");
                setCascad(ctField, "OneToMany");


                findAndRemoveMethod(ctClass, ctField, mappedByClassName);
                findAndRemoveMethod(ctClass,ctField.getName());
                String propertyName = mappedByFieldName.substring(0, 1).toUpperCase() + mappedByFieldName.substring(1);
                String getter = "set" + propertyName;

                CtMethod wow = CtMethod.make(
                        format("public net.csdn.jpa.association.Association {}() {" +
                                "net.csdn.jpa.association.Association obj = new net.csdn.jpa.association.Association(this,\"{}\",\"{}\",\"{}\");return obj;" +
                                "    }", ctField.getName(), ctField.getName(), mappedByFieldName, "javax.persistence.OneToMany"
                        )
                        ,
                        ctClass);
                ctClass.addMethod(wow);

                CtMethod wow2 = CtMethod.make(
                        format("public {} {}({} obj) {" +
                                "        this.{}.add(obj);" +
                                "        obj.{}(this);" +
                                "        return this;" +
                                "    }", ctClass.getName(), ctField.getName(), clzzName, ctField.getName(), getter
                        )
                        ,
                        ctClass);
                ctClass.addMethod(wow2);

            }


            if (EnhancerHelper.hasAnnotation(ctField, "javax.persistence.ManyToOne")) {

                String clzzName = ctField.getType().getName();

                String mappedByFieldName = findAssociatedFieldName(ctClass, clzzName);
                String mappedByClassName = ctClass.getName();

                //默认设置为cascade = CascadeType.PERSIST
                setCascad(ctField, "ManyToOne");


                findAndRemoveMethod(ctClass, ctField, mappedByClassName);
                findAndRemoveMethod(ctClass,ctField.getName());
                String propertyName = mappedByFieldName.substring(0, 1).toUpperCase() + mappedByFieldName.substring(1);
                String getter = "get" + propertyName;

                CtMethod wow = CtMethod.make(
                        format("public net.csdn.jpa.association.Association {}() {" +
                                "net.csdn.jpa.association.Association obj = new net.csdn.jpa.association.Association(this,\"{}\",\"{}\",\"{}\");return obj;" +
                                "    }", ctField.getName(), ctField.getName(), mappedByFieldName, "javax.persistence.ManyToOne"
                        ),
                        ctClass);
                ctClass.addMethod(wow);


                CtMethod wow2 = CtMethod.make(
                        format("public {} {}({} obj) {" +
                                "        this.{} = obj;" +
                                "        obj.{}().add(this);" +
                                "        return this;" +
                                "    }", ctClass.getName(), ctField.getName(), clzzName, ctField.getName(), getter
                        ),
                        ctClass);
                ctClass.addMethod(wow2);


            }
            if (EnhancerHelper.hasAnnotation(ctField, "javax.persistence.ManyToMany")) {

                String clzzName = findAssociatedClassName(ctField);

                String mappedByFieldName = findAssociatedFieldName(ctClass, clzzName);
                String mappedByClassName = ctClass.getName();

                CtField other = findAssociatedField(ctClass, clzzName);

                DBInfo dbInfo = ServiceFramwork.injector.getInstance(DBInfo.class);
                String otherClassSimpleName = findAssociatedClass(ctClass.getClassPool(), ctField).getSimpleName();


                String maybeTable1 = ctClass.getSimpleName() + "_" + otherClassSimpleName;
                String maybeTable2 = otherClassSimpleName + "_" + ctClass.getSimpleName();
                String finalTableName = dbInfo.tableNames.contains(maybeTable1) ? maybeTable1 : maybeTable2;
                setCascad(ctField, "ManyToMany");
                boolean isMaster = false;
                if (!ctField.hasAnnotation(ManyToManyHint.class)) {
                    if (dbInfo.tableNames.contains(maybeTable1)) {
                        setMappedBy(other, ctField.getName(), "ManyToMany");
                        isMaster = true;
                        finalTableName = maybeTable1;

                    }

                    if (dbInfo.tableNames.contains(maybeTable2)) {
                        setMappedBy(ctField, mappedByFieldName, "ManyToMany");
                        finalTableName = maybeTable2;
                    }
                    setManyToManyHint(other);
                }


                findAndRemoveMethod(ctClass, ctField, mappedByClassName);
                findAndRemoveMethod(ctClass,ctField.getName());
                String propertyName = mappedByFieldName.substring(0, 1).toUpperCase() + mappedByFieldName.substring(1);
                String getter = "get" + propertyName;

                CtMethod wow = CtMethod.make(
                        format("public net.csdn.jpa.association.Association {}() {" +
                                "net.csdn.jpa.association.Association obj = new net.csdn.jpa.association.Association(this,\"{}\",\"{}\",\"{}\",\"{}\",\"{}\");return obj;" +
                                "    }", ctField.getName(), ctField.getName(), mappedByFieldName, "javax.persistence.ManyToMany", finalTableName, isMaster
                        ),
                        ctClass);
                ctClass.addMethod(wow);

                CtMethod wow2 = CtMethod.make(
                        format("public {} {}({} obj) {" +
                                "        {}.add(obj);" +
                                "        obj.{}().add(this);" +
                                "        return this;" +
                                "    }", ctClass.getName(), ctField.getName(), clzzName, ctField.getName(), getter)
                        ,
                        ctClass);
                ctClass.addMethod(wow2);


            }
        }
        ctClass.defrost();
    }

    private void findAndRemoveMethod(CtClass ctClass, CtField ctField, String className) {

        try {
            CtMethod ctMethod = ctClass.getDeclaredMethod(ctField.getName(), new CtClass[]{ctClass.getClassPool().get(className)});
            ctClass.getClassFile().getMethods().remove(ctMethod.getMethodInfo());
        } catch (Exception e) {
        }

    }


    private void findAndRemoveMethod(CtClass ctClass, String methodName) throws NotFoundException {
        try {
            CtMethod ctMethod = ctClass.getDeclaredMethod(methodName);
            ctClass.getClassFile().getMethods().remove(ctMethod.getMethodInfo());
        } catch (Exception e) {
        }
    }

    private CtField findAssociatedField(CtClass ctClass, String targetClassName) throws Exception {
        return AssociatedHelper.findAssociatedField(ctClass, targetClassName);
    }

    private String findAssociatedFieldName(CtClass ctClass, String targetClassName) throws Exception {
        return AssociatedHelper.findAssociatedFieldName(ctClass, targetClassName);
    }

    private CtClass findAssociatedClass(ClassPool classPool, CtField ctField) {
        return AssociatedHelper.findAssociatedClass(classPool, ctField);
    }

    private String findAssociatedClassName(CtField ctField) {
        return AssociatedHelper.findAssociatedClassName(ctField);
    }

    private void setCascad(CtField ctField, String type) {
        AssociatedHelper.setCascadeWithDefault(ctField, type);
    }

    private void setManyToManyHint(CtField ctField) {
        AnnotationsAttribute annotationsAttribute = EnhancerHelper.getAnnotations(ctField);
        EnhancerHelper.createAnnotation(annotationsAttribute, ManyToManyHint.class);
    }

    private void setMappedBy(CtField ctField, String mappedByFieldName, String type) {
        AssociatedHelper.setMappedBy(ctField, mappedByFieldName, type);
    }
}
