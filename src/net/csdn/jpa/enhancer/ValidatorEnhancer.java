package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import net.csdn.annotation.Validate;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;

import java.lang.reflect.Modifier;


/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-2
 * Time: 下午8:37
 */
public class ValidatorEnhancer implements BitEnhancer {

    private Settings settings;

    public ValidatorEnhancer(Settings settings) {
        this.settings = settings;
    }

    @Override
    public void enhance(CtClass ctClass) {
//        CtField[] ctFields = ctClass.getFields();
//        for (CtField ctField : ctFields) {
//
//            //静态,私有，以$开头的字段,加@Validate注解，符合这四个条件为验证用的字段
//            if (!Modifier.isPrivate(ctField.getModifiers())
//                    || !Modifier.isStatic(ctField.getModifiers())
//                    || !ctField.getName().startsWith("$")) continue;
//            if (!ctField.hasAnnotation(Validate.class)) continue;
//
//            String name = ctField.getName().substring(1);
//
//        }
    }


}
