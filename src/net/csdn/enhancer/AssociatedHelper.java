package net.csdn.enhancer;

import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.EnumMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import net.csdn.ServiceFramwork;
import net.csdn.annotation.association.ManyToManyHint;
import net.csdn.jpa.enhancer.ModelClass;
import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-7-25
 * Time: 下午7:14
 */
public class AssociatedHelper {
    public static CtField findAssociatedField(ModelClass modelClass, String targetClassName) throws Exception {
        CtClass ctClass = modelClass.originClass;
        CtClass other = ctClass.getClassPool().get(targetClassName);
        CtField[] ctFields = getDeclaredFields(ModelClass.findModelClass(other));
        for (CtField otherField : ctFields) {
            if (Modifier.isFinal(otherField.getModifiers()) || Modifier.isStatic(otherField.getModifiers()))
                continue;
            String wow = findAssociatedClassName(otherField);
            CtClass wowCtClass = ServiceFramwork.classPool.get(wow);
            if (wowCtClass.subtypeOf(ctClass) && EnhancerHelper.hasAnnotationWithPrefix(other, "javax.persistence.")) {
                return otherField;
            }
        }
        return null;
    }

    public static CtField[] getDeclaredFields(ModelClass modelClass) throws Exception {
        List<CtField> ctFields = new ArrayList<CtField>();
        if (modelClass.isInheritance()) {
            for (CtField ctField : modelClass.originClass.getSuperclass().getDeclaredFields()) {
                ctFields.add(ctField);
            }
            for (CtField ctField : modelClass.originClass.getDeclaredFields()) {
                ctFields.add(ctField);
            }
            CtField[] ctFields1 = new CtField[ctFields.size()];
            ctFields.toArray(ctFields1);
            return ctFields1;
        } else {
            return modelClass.originClass.getDeclaredFields();
        }
    }

    public static void findAndRemoveMethod(CtClass ctClass, String methodName) throws NotFoundException {
        try {
            CtMethod ctMethod = ctClass.getDeclaredMethod(methodName);
            ctClass.getClassFile().getMethods().remove(ctMethod.getMethodInfo());
        } catch (Exception e) {
        }
    }

    public static void findAndRemoveMethod(CtClass ctClass, CtField ctField, String className) {

        try {
            CtMethod ctMethod = ctClass.getDeclaredMethod(ctField.getName(), new CtClass[]{ctClass.getClassPool().get(className)});
            ctClass.getClassFile().getMethods().remove(ctMethod.getMethodInfo());
        } catch (Exception e) {
        }

    }

    public static String findAssociatedFieldName(ModelClass modelClass, String targetClassName) throws Exception {
        CtField ctField = findAssociatedField(modelClass, targetClassName);
        if (ctField != null) return ctField.getName();
        return null;
    }

    public static CtClass findAssociatedClass(ClassPool classPool, CtField ctField) {
        try {
            return classPool.get(findAssociatedClassName(ctField));
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String findAssociatedClassName(CtField ctField) {
        SignatureAttribute.ObjectType objectType = EnhancerHelper.getFieldSignature(ctField);
        String className = EnhancerHelper.findFieldGenericType(objectType);
        if (StringUtils.isEmpty(className)) {
            try {
                return ctField.getType().getName();
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        }
        return className;
    }

    public static void setCascadeWithDefault(CtField ctField, String type) {
        setCascade(ctField, type, "PERSIST");
    }

    public static void setCascade(CtField ctField, String type, String... persistTypes) {
        //默认设置为cascade = CascadeType.PERSIST
        AnnotationsAttribute annotationsAttribute = EnhancerHelper.getAnnotations(ctField);
        Annotation annotation = annotationsAttribute.getAnnotation("javax.persistence." + type);
        ArrayMemberValue cascade = (ArrayMemberValue) annotation.getMemberValue("cascade");
        if (cascade == null || cascade.getValue().length == 0) {

            EnumMemberValue[] enumMemberValue = new EnumMemberValue[persistTypes.length];

            int i = 0;
            for (String persistType : persistTypes) {
                EnumMemberValue emb = new EnumMemberValue(ctField.getFieldInfo2().getConstPool());
                emb.setType("javax.persistence.CascadeType");
                emb.setValue(persistType);
                enumMemberValue[i++] = emb;
            }

            ArrayMemberValue arrayMemberValue = new ArrayMemberValue(ctField.getFieldInfo2().getConstPool());
            arrayMemberValue.setValue(enumMemberValue);
            annotation.addMemberValue("cascade", arrayMemberValue);
            annotationsAttribute.addAnnotation(annotation);
        }
    }

    public static void setManyToManyHint(CtField ctField) {
        AnnotationsAttribute annotationsAttribute = EnhancerHelper.getAnnotations(ctField);
        EnhancerHelper.createAnnotation(annotationsAttribute, ManyToManyHint.class);
    }

    public static void setMappedBy(CtField ctField, String mappedByFieldName, String type) {
        AnnotationsAttribute annotationsAttribute = EnhancerHelper.getAnnotations(ctField);
        ConstPool constPool = ctField.getFieldInfo2().getConstPool();
        Annotation annotation = annotationsAttribute.getAnnotation("javax.persistence." + type);
        StringMemberValue mappedBy = (StringMemberValue) annotation.getMemberValue("mappedBy");
        if (mappedBy == null || StringUtils.isEmpty(mappedBy.getValue())) {
            annotation.addMemberValue("mappedBy", new StringMemberValue(mappedByFieldName, constPool));
            annotationsAttribute.addAnnotation(annotation);
        }
    }
}
