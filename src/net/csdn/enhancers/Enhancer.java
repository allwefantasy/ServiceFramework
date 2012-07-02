package net.csdn.enhancers;

import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.MemberValue;
import net.csdn.bootstrap.Bootstrap;

import java.io.DataInputStream;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-6-26
 * Time: 下午10:08
 */
public abstract class Enhancer {
    protected ClassPool classPool;

    public Enhancer() {
        this.classPool = Bootstrap.classPool;
    }


//    public Class loadClass(String className) {
//        try {
//            return classPool.get(className).getClass();
//        } catch (NotFoundException e) {
//            throw new JPAQueryException(format("无法知道className为{}的类", className), e.getCause());
//        }
//    }

    public abstract void enhanceThisClass(DataInputStream dataInputStream) throws Exception;

    protected boolean hasAnnotation(CtClass ctClass, String annotation) throws ClassNotFoundException {
        for (Object object : ctClass.getAvailableAnnotations()) {
            Annotation ann = (Annotation) object;
            if (ann.annotationType().getName().equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasAnnotation(CtField ctField, String annotation) throws ClassNotFoundException {
        for (Object object : ctField.getAvailableAnnotations()) {
            Annotation ann = (Annotation) object;
            if (ann.annotationType().getName().equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasAnnotation(CtMethod ctMethod, String annotation) throws ClassNotFoundException {
        for (Object object : ctMethod.getAvailableAnnotations()) {
            Annotation ann = (Annotation) object;
            if (ann.annotationType().getName().equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    protected static void createAnnotation(AnnotationsAttribute attribute, Class<? extends Annotation> annotationType, Map<String, MemberValue> members) {
        javassist.bytecode.annotation.Annotation annotation = new javassist.bytecode.annotation.Annotation(annotationType.getName(), attribute.getConstPool());
        for (Map.Entry<String, MemberValue> member : members.entrySet()) {
            annotation.addMemberValue(member.getKey(), member.getValue());
        }
        attribute.addAnnotation(annotation);
    }

    protected static void createAnnotation(AnnotationsAttribute attribute, Class<? extends Annotation> annotationType) {
        createAnnotation(attribute, annotationType, new HashMap<String, MemberValue>());
    }


    protected static AnnotationsAttribute getAnnotations(CtClass ctClass) {
        AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) ctClass.getClassFile().getAttribute(AnnotationsAttribute.visibleTag);
        if (annotationsAttribute == null) {
            annotationsAttribute = new AnnotationsAttribute(ctClass.getClassFile().getConstPool(), AnnotationsAttribute.visibleTag);
            ctClass.getClassFile().addAttribute(annotationsAttribute);
        }
        return annotationsAttribute;
    }

    protected static AnnotationsAttribute getAnnotations(CtField ctField) {
        AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) ctField.getFieldInfo().getAttribute(AnnotationsAttribute.visibleTag);
        if (annotationsAttribute == null) {
            annotationsAttribute = new AnnotationsAttribute(ctField.getFieldInfo().getConstPool(), AnnotationsAttribute.visibleTag);
            ctField.getFieldInfo().addAttribute(annotationsAttribute);
        }
        return annotationsAttribute;
    }


    protected static AnnotationsAttribute getAnnotations(CtMethod ctMethod) {
        AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) ctMethod.getMethodInfo().getAttribute(AnnotationsAttribute.visibleTag);
        if (annotationsAttribute == null) {
            annotationsAttribute = new AnnotationsAttribute(ctMethod.getMethodInfo().getConstPool(), AnnotationsAttribute.visibleTag);
            ctMethod.getMethodInfo().addAttribute(annotationsAttribute);
        }
        return annotationsAttribute;
    }


}
