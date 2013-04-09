package net.csdn.enhancer;

/**
 * 4/9/13 WilliamZhu(allwefantasy@gmail.com)
 */

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.MemberValue;
import net.csdn.ServiceFramwork;
import net.csdn.common.enhancer.EnhancerHelper;

import java.io.DataInputStream;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 4/9/13 WilliamZhu(allwefantasy@gmail.com)
 */
public abstract class ControllerEnhancer {
    protected ClassPool classPool;

    public ControllerEnhancer() {
        this.classPool = ServiceFramwork.classPool;
    }


    public abstract CtClass enhanceThisClass(DataInputStream dataInputStream) throws Exception;

    public abstract void enhanceThisClass2(List<CtClass> ctClasses) throws Exception;

    protected boolean hasAnnotation(CtClass ctClass, String annotation) throws ClassNotFoundException {
        return EnhancerHelper.hasAnnotation(ctClass, annotation);
    }

    protected boolean hasAnnotation(CtField ctField, String annotation) throws ClassNotFoundException {
        return EnhancerHelper.hasAnnotation(ctField, annotation);
    }

    protected boolean hasAnnotation(CtMethod ctMethod, String annotation) throws ClassNotFoundException {
        return EnhancerHelper.hasAnnotation(ctMethod, annotation);
    }

    protected static void createAnnotation(AnnotationsAttribute attribute, Class<? extends Annotation> annotationType, Map<String, MemberValue> members) {
        EnhancerHelper.createAnnotation(attribute, annotationType, members);
    }

    protected static void createAnnotation(AnnotationsAttribute attribute, Class<? extends Annotation> annotationType) {
        createAnnotation(attribute, annotationType, new HashMap<String, MemberValue>());
    }


    protected static AnnotationsAttribute getAnnotations(CtClass ctClass) {
        return EnhancerHelper.getAnnotations(ctClass);
    }

    protected static AnnotationsAttribute getAnnotations(CtField ctField) {
        return EnhancerHelper.getAnnotations(ctField);
    }


    protected static AnnotationsAttribute getAnnotations(CtMethod ctMethod) {
        return EnhancerHelper.getAnnotations(ctMethod);
    }


}

