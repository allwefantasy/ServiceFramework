package net.csdn.jpa.enhancer;

import javassist.*;
import javassist.bytecode.*;
import javassist.bytecode.annotation.*;
import net.csdn.ServiceFramwork;
import net.csdn.annotation.ManyToManyHint;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.AssociatedHelper;
import net.csdn.enhancer.BitEnhancer;
import net.csdn.enhancer.EnhancerHelper;
import net.csdn.jpa.type.DBInfo;
import org.apache.commons.lang.StringUtils;


import java.lang.reflect.Modifier;

import static net.csdn.common.logging.support.MessageFormat.format;


/**
 * BlogInfo: WilliamZhu
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
        for (CtField ctField : fields) {


            if (EnhancerHelper.hasAnnotation(ctField, "javax.persistence.OneToMany")) {


                String clzzName = findAssociatedClassName(ctField);

                String mappedByFieldName = findAssociatedFieldName(ctClass, clzzName);
                String mappedByClassName = ctClass.getName();

                //如果没有设置mappedBy我们帮他设置吧
                setMappedBy(ctField, mappedByFieldName, "OneToMany");


                try {
                    findMethod(ctClass, ctField, mappedByClassName);
                } catch (NotFoundException e) {
                    String propertyName = mappedByFieldName.substring(0, 1).toUpperCase() + mappedByFieldName.substring(1);
                    String getter = "set" + propertyName;

                    CtMethod wow = CtMethod.make(
                            format("public {} {}({} obj) {" +
                                    "        this.{}.add(obj);" +
                                    "        obj.{}(this);" +
                                    "        return this;" +
                                    "    }", ctClass.getName(), ctField.getName(), clzzName, ctField.getName(), getter
                            )
                            ,
                            ctClass);
                    ctClass.addMethod(wow);
                }
            }


            if (EnhancerHelper.hasAnnotation(ctField, "javax.persistence.ManyToOne")) {

                String clzzName = ctField.getType().getName();

                String mappedByFieldName = findAssociatedFieldName(ctClass, clzzName);
                String mappedByClassName = ctClass.getName();

                //默认设置为cascade = CascadeType.PERSIST
                setCascad(ctField, "ManyToOne");


                try {
                    findMethod(ctClass, ctField, mappedByClassName);
                } catch (NotFoundException e) {
                    String propertyName = mappedByFieldName.substring(0, 1).toUpperCase() + mappedByFieldName.substring(1);
                    String getter = "get" + propertyName;

                    CtMethod wow = CtMethod.make(
                            format("public {} {}({} obj) {" +
                                    "        this.{} = obj;" +
                                    "        obj.{}().add(this);" +
                                    "        return this;" +
                                    "    }", ctClass.getName(), ctField.getName(), clzzName, ctField.getName(), getter
                            ),
                            ctClass);
                    ctClass.addMethod(wow);
                }


            }
            if (EnhancerHelper.hasAnnotation(ctField, "javax.persistence.ManyToMany")) {

                String clzzName = findAssociatedClassName(ctField);

                String mappedByFieldName = findAssociatedFieldName(ctClass, clzzName);
                String mappedByClassName = ctClass.getName();

                CtField other = findAssociatedField(ctClass, clzzName);

                if (!ctField.hasAnnotation(ManyToManyHint.class)) {
                    DBInfo dbInfo = ServiceFramwork.injector.getInstance(DBInfo.class);
                    String otherClassSimpleName = findAssociatedClass(ctClass.getClassPool(), ctField).getSimpleName();
                    String maybeTable1 = ctClass.getSimpleName() + "_" + otherClassSimpleName;
                    String maybeTable2 = otherClassSimpleName + "_" + ctClass.getSimpleName();


                    if (dbInfo.tableNames.contains(maybeTable1)) {
                        //setCascad(other, "ManyToMany");
                        setMappedBy(other, ctField.getName(), "ManyToMany");

                    }

                    if (dbInfo.tableNames.contains(maybeTable2)) {
                        // setCascad(ctField, "ManyToMany");
                        setMappedBy(ctField, mappedByFieldName, "ManyToMany");
                    }
                    setManyToManyHint(other);
                }

                try {

                    findMethod(ctClass, ctField, mappedByClassName);
                } catch (NotFoundException e) {
                    String propertyName = mappedByFieldName.substring(0, 1).toUpperCase() + mappedByFieldName.substring(1);
                    String getter = "get" + propertyName;

                    CtMethod wow = CtMethod.make(
                            format("public {} {}({} obj) {" +
                                    "        {}.add(obj);" +
                                    "        obj.{}().add(this);" +
                                    "        return this;" +
                                    "    }", ctClass.getName(), ctField.getName(), clzzName, ctField.getName(), getter)
                            ,
                            ctClass);
                    ctClass.addMethod(wow);
                }
            }
        }
        ctClass.defrost();
    }

    private void findMethod(CtClass ctClass, CtField ctField, String className) throws NotFoundException {
        if (StringUtils.isEmpty(className)) throw new NotFoundException("猜测没有");
        CtMethod ctMethod = ctClass.getDeclaredMethod(ctField.getName(), new CtClass[]{ctClass.getClassPool().get(className)});
        if (Modifier.isStatic(ctMethod.getModifiers()) || Modifier.isFinal(ctMethod.getModifiers())) {
            throw new NotFoundException("这个方法已经出现了，并且被设置为static 或者final");
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
