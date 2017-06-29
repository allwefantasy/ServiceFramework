package net.csdn.jpa.enhancer;

import com.google.common.collect.Lists;
import javassist.CtClass;
import net.csdn.common.settings.Settings;
import net.csdn.enhancer.ActiveORMEnhancer;

import java.io.DataInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.csdn.common.collections.WowCollections.list;


/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-26
 * Time: 下午10:11
 */
public class JPAEnhancer extends ActiveORMEnhancer {

    private Settings settings;


    public JPAEnhancer(Settings settings) {
        this.settings = settings;
    }


    public CtClass enhanceThisClass(DataInputStream dataInputStream) throws Exception {
        CtClass ctClass = classPool.makeClassIfNew(dataInputStream);

        if (!ctClass.subtypeOf(classPool.get(ModelClass.MODEL_CLASS_NAME))) {
            return ctClass;
        }

        return ctClass;

    }

    private void buildModelClassTree(List<ModelClass> modelClasses) throws Exception {

        for (ModelClass modelClass : modelClasses) {
            if (modelClass.originClass.getSuperclass().getName().equals(ModelClass.MODEL_CLASS_NAME)) {
                ModelClass.ROOTS.add(modelClass);
                buildRelationship(modelClasses, modelClass);
            }
        }
    }

    private void buildRelationship(List<ModelClass> modelClasses, ModelClass modelClass) {
        for (ModelClass temp : modelClasses) {
            if (temp.originClass.subclassOf(modelClass.originClass) && temp.originClass != modelClass.originClass) {
                modelClass.addChild(temp);
                temp.parent(modelClass);
                buildRelationship(modelClasses, temp);
            }
        }
    }


    public List<ModelClass> enhanceThisClass2(List<CtClass> ctClasses) throws Exception {

        List<ModelClass> modelClasses = list();
        for (CtClass ct : ctClasses) {
            ModelClass modelClass = new ModelClass(ct);
            modelClasses.add(modelClass);
            ModelClass.CTModelClasses.put(ct, modelClass);
        }

        buildModelClassTree(modelClasses);

        Set<ModelClass> result = new HashSet();

        for (ModelClass modelClass : ModelClass.ROOTS) {
            if (modelClass.isLeafNode()) {
                result.add(modelClass);
                continue;
            }
            ;
            result.addAll(modelClass.findLeafNodes());
        }
        new EntityEnhancer(settings).enhance(ModelClass.ROOTS);
        new ClassMethodEnhancer(settings).enhance(Lists.newArrayList(result));
        new AssociationEnhancer(settings).enhance(Lists.newArrayList(result));
        return ModelClass.ROOTS;
    }


}
