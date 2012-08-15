package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.BooleanMemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import net.csdn.common.Strings;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;
import net.csdn.enhancer.Enhancer;
import org.hibernate.annotations.DynamicInsert;

import javax.persistence.Entity;
import javax.persistence.Table;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;

import static net.csdn.common.collections.WowCollections.map;


/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-26
 * Time: 下午10:11
 */
public class JPAEnhancer extends Enhancer {

    private Settings settings;
    private List<BitEnhancer> bitEnhancers = new ArrayList<BitEnhancer>();

    public JPAEnhancer(Settings settings) {
        this.settings = settings;
        bitEnhancers.add(new PropertyEnhancer(settings));
        bitEnhancers.add(new ClassMethodEnhancer(settings));
        bitEnhancers.add(new InstanceMethodEnhancer(settings));
    }

    public CtClass enhanceThisClass(DataInputStream dataInputStream) throws Exception {
        CtClass ctClass = classPool.makeClassIfNew(dataInputStream);

        if (!ctClass.subtypeOf(classPool.get("net.csdn.jpa.model.JPABase"))) {
            return ctClass;
        }

        // Enhance only JPA entities
//        if (!hasAnnotation(ctClass, "javax.persistence.Entity")) {
//            return ctClass;
//        }

        //Warning: here stick to hibernate
        ConstPool constPool = ctClass.getClassFile().getConstPool();
        AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        createAnnotation(attr,Entity.class);
        createAnnotation(attr, Table.class, map("name", new StringMemberValue(Strings.toUnderscoreCase(ctClass.getSimpleName()), constPool)));
        createAnnotation(attr, org.hibernate.annotations.Entity.class, map("dynamicInsert", new BooleanMemberValue(true, constPool)
        ));
        createAnnotation(attr, DynamicInsert.class);
        ctClass.getClassFile().addAttribute(attr);

        //done
        return ctClass;

    }

    public void enhanceThisClass2(List<CtClass> ctClasses) throws Exception {
        for (BitEnhancer bitEnhancer : bitEnhancers) {
            for (CtClass ctClass : ctClasses) {
                // Bootstrap.isLoaded(ctClass.getName());
                bitEnhancer.enhance(ctClass);
            }
        }
    }


}
