package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import net.csdn.annotation.Hint;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;

import javax.persistence.OneToMany;

import java.lang.reflect.Modifier;

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

    @Override
    public void enhance(CtClass ctClass) throws Exception {
        CtField[] fields = ctClass.getDeclaredFields();
        for (CtField ctField : fields) {
            if (ctField.hasAnnotation(OneToMany.class)) {
                OneToMany oneToMany = (OneToMany) ctField.getAnnotation(OneToMany.class);
                Hint hint = (Hint) ctField.getAnnotation(Hint.class);
                String clzzName = hint.value().getName();


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
        }
        ctClass.defrost();
    }
}
