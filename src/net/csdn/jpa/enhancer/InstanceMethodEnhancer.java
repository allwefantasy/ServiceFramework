package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.*;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.EnumMemberValue;
import net.csdn.annotation.Hint;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;
import net.csdn.enhancer.EnhancerHelper;

import javax.persistence.CascadeType;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;

import java.lang.reflect.Modifier;
import java.util.List;

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

    @Override
    public void enhance(CtClass ctClass) throws Exception {
        CtField[] fields = ctClass.getDeclaredFields();
        for (CtField ctField : fields) {
            //AnnotationsAttribute annotationsAttribute = (AnnotationsAttribute) ctField.getFieldInfo2().getAttribute(AnnotationsAttribute.visibleTag);
            //ConstPool constPool = annotationsAttribute.getConstPool();
            if (ctField.hasAnnotation(OneToMany.class)) {
                OneToMany oneToMany = (OneToMany) ctField.getAnnotation(OneToMany.class);

//                if (oneToMany.mappedBy().isEmpty()) {
//                    if (oneToMany.cascade().length == 0) {
//                        EnumMemberValue enumMemberValue = new EnumMemberValue(constPool);
//                        enumMemberValue.setType(CascadeType.class.getName());
//                        enumMemberValue.setValue(CascadeType.ALL.name());
//                        EnhancerHelper.modifyAnnotation(annotationsAttribute, oneToMany, "cascade", new ArrayMemberValue(enumMemberValue, constPool));
//                    }
//                }


                SignatureAttribute.ObjectType objectType = EnhancerHelper.getFieldSignature(ctField);
                String clzzName = EnhancerHelper.findFieldGenericType(objectType);
                try {
                    CtMethod ctMethod = ctClass.getDeclaredMethod(ctField.getName());
                    if (Modifier.isStatic(ctMethod.getModifiers()) || Modifier.isFinal(ctMethod.getModifiers())) {
                        throw new NotFoundException("no ,not what i want");
                    }
                } catch (NotFoundException e) {
                    CtMethod wow = CtMethod.make(
                            format("public " + clzzName + " " + ctField.getName() + "() {{};{};{};return obj;}",
                                    clzzName + " obj = new " + clzzName + "();",
                                    "obj.attr(\"" + oneToMany.mappedBy() + "\",this)",
                                    ctField.getName() + ".add(obj)"
                            ),
                            ctClass);
                    ctClass.addMethod(wow);
                }


            }
//            if (ctField.hasAnnotation(OneToOne.class)) {
//                OneToOne oneToOne = (OneToOne) ctField.getAnnotation(OneToOne.class);
//                if (oneToOne.mappedBy().isEmpty()) {
//                    if (oneToOne.cascade().length == 0) {
//                        EnumMemberValue enumMemberValue = new EnumMemberValue(constPool);
//                        enumMemberValue.setType(CascadeType.class.getName());
//                        enumMemberValue.setValue(CascadeType.ALL.name());
//                        EnhancerHelper.modifyAnnotation(annotationsAttribute, oneToOne, "cascade", new ArrayMemberValue(enumMemberValue, constPool));
//                    }
//                }
//            }
        }
        ctClass.defrost();
    }
}
