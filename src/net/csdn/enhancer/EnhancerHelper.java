package net.csdn.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.ConstPool;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.*;
import net.csdn.reflect.ReflectHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * User: WilliamZhu
 * Date: 12-7-12
 * Time: 下午1:35
 */
public class EnhancerHelper {

    public static String findFieldGenericType(SignatureAttribute.ObjectType fieldSignatureType) {
        try {
            Field field = fieldSignatureType.getClass().getDeclaredField("arguments");
            field.setAccessible(true);

            SignatureAttribute.TypeArgument[] arguments = (SignatureAttribute.TypeArgument[]) field.get(fieldSignatureType);
            return arguments[0].toString();
        } catch (Exception e) {

        }
        return null;
    }

    public static SignatureAttribute.ObjectType getFieldSignature(CtField field) {
        if (field == null)
            throw new IllegalArgumentException("Null method/constructor");
        SignatureAttribute signature = (SignatureAttribute) field.getFieldInfo2().getAttribute(SignatureAttribute.tag);
        if (signature == null)
            return null;
        String sig = signature.getSignature();
        try {
            return SignatureAttribute.toFieldSignature(sig);
        } catch (BadBytecode e) {
            throw new IllegalStateException(e);
        }
    }

    public static void createAnnotation(AnnotationsAttribute attribute, Class<? extends Annotation> annotationType, Map<String, MemberValue> members) {
        javassist.bytecode.annotation.Annotation annotation = new javassist.bytecode.annotation.Annotation(annotationType.getName(), attribute.getConstPool());
        for (Map.Entry<String, MemberValue> member : members.entrySet()) {
            annotation.addMemberValue(member.getKey(), member.getValue());
        }
        attribute.addAnnotation(annotation);
    }


    public static boolean hasAnnotation(CtClass ctClass, String annotation) throws ClassNotFoundException {
        for (Object object : ctClass.getAvailableAnnotations()) {
            Annotation ann = (Annotation) object;
            if (ann.annotationType().getName().equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnnotationWithPrefix(CtClass ctClass, String annotationPrefix) throws ClassNotFoundException {
        for (Object object : ctClass.getAvailableAnnotations()) {
            Annotation ann = (Annotation) object;
            if (ann.annotationType().getName().startsWith(annotationPrefix)) {
                return true;
            }
        }
        return false;
    }


    public static boolean hasAnnotation(CtField ctField, String annotation) throws ClassNotFoundException {
        for (Object object : ctField.getAvailableAnnotations()) {
            Annotation ann = (Annotation) object;
            if (ann.annotationType().getName().equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasAnnotation(CtMethod ctMethod, String annotation) throws ClassNotFoundException {
        for (Object object : ctMethod.getAvailableAnnotations()) {
            Annotation ann = (Annotation) object;
            if (ann.annotationType().getName().equals(annotation)) {
                return true;
            }
        }
        return false;
    }


    public static AnnotationsAttribute getAnnotations(CtClass ctClass) {
        AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) ctClass.getClassFile().getAttribute(AnnotationsAttribute.visibleTag);
        if (annotationsAttribute == null) {
            annotationsAttribute = new AnnotationsAttribute(ctClass.getClassFile().getConstPool(), AnnotationsAttribute.visibleTag);
            ctClass.getClassFile().addAttribute(annotationsAttribute);
        }
        return annotationsAttribute;
    }

    public static AnnotationsAttribute getAnnotations(CtField ctField) {
        AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) ctField.getFieldInfo().getAttribute(AnnotationsAttribute.visibleTag);
        if (annotationsAttribute == null) {
            annotationsAttribute = new AnnotationsAttribute(ctField.getFieldInfo().getConstPool(), AnnotationsAttribute.visibleTag);
            ctField.getFieldInfo().addAttribute(annotationsAttribute);
        }
        return annotationsAttribute;
    }


    public static AnnotationsAttribute getAnnotations(CtMethod ctMethod) {
        AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) ctMethod.getMethodInfo().getAttribute(AnnotationsAttribute.visibleTag);
        if (annotationsAttribute == null) {
            annotationsAttribute = new AnnotationsAttribute(ctMethod.getMethodInfo().getConstPool(), AnnotationsAttribute.visibleTag);
            ctMethod.getMethodInfo().addAttribute(annotationsAttribute);
        }
        return annotationsAttribute;
    }

//    public static void modifyAnnotation(AnnotationsAttribute attribute, Annotation annotation, String key, MemberValue memberValue) {
//
//        Method[] methods = annotation.getClass().getDeclaredMethods();
//        Map<String, MemberValue> memberValueMap = new HashMap<String, MemberValue>();
//        for (Method method : methods) {
//            if (method.getName().equals(key)) {
//                memberValueMap.put(key, memberValue);
//            } else {
//                Object obj = ReflectHelper.method(annotation, method.getName());
//                if (obj == null) continue;
//                MemberValue temp = createMemberValue(attribute.getConstPool(), method.getReturnType(), obj);
//                memberValueMap.put(method.getName(), temp);
//            }
//        }
//        createAnnotation(attribute, annotation.annotationType(), memberValueMap);
//
//    }

    public static void createAnnotation(AnnotationsAttribute attribute, Class<? extends Annotation> annotationType) {
        createAnnotation(attribute, annotationType, new HashMap<String, MemberValue>());
    }

//    public static MemberValue createMemberValue(ConstPool cp, Class type, Object obj)
//
//    {
//        if (type == Boolean.class) {
//            BooleanMemberValue booleanMemberValue = new BooleanMemberValue(cp);
//            booleanMemberValue.setValue((Boolean) obj);
//            return booleanMemberValue;
//        } else if (type == Byte.class)
//            return new ByteMemberValue(cp);
//        else if (type == Character.class)
//            return new CharMemberValue(cp);
//        else if (type == Short.class)
//            return new ShortMemberValue(cp);
//        else if (type == Integer.class)
//            return new IntegerMemberValue(cp);
//        else if (type == Long.class)
//            return new LongMemberValue(cp);
//        else if (type == Float.class)
//            return new FloatMemberValue(cp);
//        else if (type == Double.class)
//            return new DoubleMemberValue(cp);
//        else if (type == Class.class)
//            return new ClassMemberValue(cp);
//        else if (type == String.class)
//            return new StringMemberValue(cp);
//        else if (type.isArray()) {
//            Class arrayType = type.getComponentType();
//            MemberValue member = createMemberValue(cp, arrayType);
//            return new ArrayMemberValue(member, cp);
//        } else if (type.isInterface()) {
////            Annotation info = new Annotation(cp, type);
////            return new AnnotationMemberValue(info, cp);
//            return null;
//        } else {
//            // treat as enum.  I know this is not typed,
//            // but JBoss has an Annotation Compiler for JDK 1.4
//            // and I want it to work with that. - Bill Burke
//            EnumMemberValue emv = new EnumMemberValue(cp);
//            emv.setType(type.getName());
//            emv.setValue(obj.toString());
//            return emv;
//        }
//    }

}
