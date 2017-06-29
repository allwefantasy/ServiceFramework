package net.csdn.common.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.annotation.MemberValue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public static void createAnnotation(CtField ctField, Class<? extends Annotation> annotationType, Map<String, MemberValue> members) {
        if (ctField.hasAnnotation(annotationType)) return;

        AnnotationsAttribute attr = new AnnotationsAttribute(ctField.getFieldInfo().getConstPool(), AnnotationsAttribute.visibleTag);
        boolean isNewAttr = true;
        List<AttributeInfo> attributeInfos = ctField.getFieldInfo().getAttributes();
        for (AttributeInfo attributeInfo : attributeInfos) {
            if (attributeInfo instanceof AnnotationsAttribute) {
                attr = (AnnotationsAttribute) attributeInfo;
                isNewAttr = false;
            }
        }

        javassist.bytecode.annotation.Annotation[] annotations = attr.getAnnotations();
        javassist.bytecode.annotation.Annotation annotation = new javassist.bytecode.annotation.Annotation(annotationType.getName(), attr.getConstPool());

        for (javassist.bytecode.annotation.Annotation annotation1 : annotations) {
            if (annotation1.getTypeName().equals(annotationType.getName())) {
                annotation = annotation1;
                break;
            }
        }

        for (Map.Entry<String, MemberValue> member : members.entrySet()) {
            try {
                if (annotation.getMemberValue(member.getKey()) == null) {
                    annotation.addMemberValue(member.getKey(), member.getValue());
                }
            } catch (Exception e) {
                e.printStackTrace();
                annotation.addMemberValue(member.getKey(), member.getValue());
            }

        }

        attr.addAnnotation(annotation);
        if (isNewAttr) {
            ctField.getFieldInfo().addAttribute(attr);
        }

    }

    public static void createAnnotation(CtClass ctClass, Class<? extends Annotation> annotationType, Map<String, MemberValue> members) {


        if (ctClass.hasAnnotation(annotationType)) return;

        AnnotationsAttribute attr = new AnnotationsAttribute(ctClass.getClassFile().getConstPool(), AnnotationsAttribute.visibleTag);
        boolean isNewAttr = true;
        List<AttributeInfo> attributeInfos = ctClass.getClassFile2().getAttributes();
        for (AttributeInfo attributeInfo : attributeInfos) {
            if (attributeInfo instanceof AnnotationsAttribute) {
                attr = (AnnotationsAttribute) attributeInfo;
                isNewAttr = false;
            }
        }

        javassist.bytecode.annotation.Annotation[] annotations = attr.getAnnotations();
        javassist.bytecode.annotation.Annotation annotation = new javassist.bytecode.annotation.Annotation(annotationType.getName(), attr.getConstPool());

        for (javassist.bytecode.annotation.Annotation annotation1 : annotations) {
            if (annotation1.getTypeName().equals(annotationType.getName())) {
                annotation = annotation1;
                break;
            }
        }

        for (Map.Entry<String, MemberValue> member : members.entrySet()) {
            try {
                if (annotation.getMemberValue(member.getKey()) == null) {
                    annotation.addMemberValue(member.getKey(), member.getValue());
                }
            } catch (Exception e) {
                e.printStackTrace();
                annotation.addMemberValue(member.getKey(), member.getValue());
            }

        }

        attr.addAnnotation(annotation);

        if (isNewAttr) {
            ctClass.getClassFile().addAttribute(attr);
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
        return annotationsAttribute;
    }


    public static AnnotationsAttribute getAnnotations(CtMethod ctMethod) {
        AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) ctMethod.getMethodInfo().getAttribute(AnnotationsAttribute.visibleTag);
        return annotationsAttribute;
    }


    public static void createAnnotation(AnnotationsAttribute attribute, Class<? extends Annotation> annotationType) {
        createAnnotation(attribute, annotationType, new HashMap<String, MemberValue>());
    }


}
