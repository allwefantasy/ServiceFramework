package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AccessFlag;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;
import net.csdn.enhancer.Enhancer;

import java.io.DataInputStream;
import java.lang.reflect.Modifier;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.newArrayList;
import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: WilliamZhu
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

    public void enhanceThisClass(DataInputStream dataInputStream) throws Exception {

        CtClass ctClass = classPool.makeClassIfNew(dataInputStream);
        if (!ctClass.subtypeOf(classPool.get("net.csdn.jpa.model.JPABase"))) {
            return;
        }

        // Enhance only JPA entities
        if (!hasAnnotation(ctClass, "javax.persistence.Entity")) {
            return;
        }

        for (BitEnhancer bitEnhancer : bitEnhancers) {
            bitEnhancer.enhance(ctClass);
        }

        //done
        ctClass.toClass();

    }


}
