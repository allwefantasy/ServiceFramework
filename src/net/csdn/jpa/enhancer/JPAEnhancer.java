package net.csdn.jpa.enhancer;

import javassist.CtClass;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.BitEnhancer;
import net.csdn.enhancer.Enhancer;

import java.io.DataInputStream;
import java.util.HashSet;
import java.util.List;

import static net.csdn.common.collections.WowCollections.list;


/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-26
 * Time: 下午10:11
 */
public class JPAEnhancer extends Enhancer {

    private Settings settings;
    private List<BitEnhancer> bitEnhancers = list();


    public JPAEnhancer(Settings settings) {
        this.settings = settings;
        bitEnhancers.add(new EntityEnhancer(settings));
        bitEnhancers.add(new PropertyEnhancer(settings));
        bitEnhancers.add(new ClassMethodEnhancer(settings));
        bitEnhancers.add(new InstanceMethodEnhancer(settings));
    }


    public CtClass enhanceThisClass(DataInputStream dataInputStream) throws Exception {
        CtClass ctClass = classPool.makeClassIfNew(dataInputStream);

        if (!ctClass.subtypeOf(classPool.get("net.csdn.jpa.model.JPABase"))) {
            return ctClass;
        }

        return ctClass;

    }

    public void enhanceThisClass2(List<CtClass> ctClasses) throws Exception {
        List<ModelClass> modelClasses = list();
        for (CtClass ctClass : ctClasses) {
            modelClasses.add(new ModelClass(ctClass, new HashSet<ModelClass>()));
        }
        for (BitEnhancer bitEnhancer : bitEnhancers) {
            bitEnhancer.enhance(modelClasses);
        }
    }


}
