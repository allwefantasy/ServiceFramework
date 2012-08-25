package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.CtField;
import net.csdn.annotation.association.NotMapping;

import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.csdn.common.collections.WowCollections.list;

/**
 * User: WilliamZhu
 * Date: 12-8-21
 * Time: 下午8:51
 */
public class ModelClass {
    public final static Set<ModelClass> fatherModelClass = new HashSet<ModelClass>();
    public final static Set<ModelClass> modelClasses = new HashSet<ModelClass>();
    public CtClass originClass;
    public Set<ModelClass> children;
    private List<String> skipFields = list();


    public ModelClass(CtClass originClass, Set<ModelClass> children) {
        this.originClass = originClass;
        this.children = children;
        notMapping(originClass, skipFields);
    }

    public static ModelClass findModelClass(CtClass ct) {
        for (ModelClass modelClass : modelClasses) {
            if (ct == modelClass.originClass) {
                return modelClass;
            }
        }
        return null;
    }

    public boolean isInheritance() {
        List<ModelClass> modelClasses = list();
        for (ModelClass modelClass : fatherModelClass) {
            modelClasses.addAll(modelClass.children);
        }
        if (modelClasses.contains(this)) {
            return true;
        }
        return false;
    }

    public List<String> notMappingColumns() {
        notMapping(originClass, skipFields);
        for (ModelClass modelClass : children) {
            notMapping(modelClass.originClass, skipFields);
        }
        return skipFields;
    }

    private void notMapping(CtClass ctClass, List<String> skipFields) {
        if (ctClass.hasAnnotation(NotMapping.class)) {
            try {
                NotMapping notMapping = (NotMapping) ctClass.getAnnotation(NotMapping.class);
                for (String str : notMapping.value()) {
                    skipFields.add(str);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        autoNotMapping(ctClass, skipFields);
    }

    //自动过滤掉
    private void autoNotMapping(CtClass ctClass, List<String> skipFields) {
        CtField[] fields = ctClass.getDeclaredFields();
        for (CtField ctField : fields) {
            guessNotMappingName(ctField, ManyToOne.class, skipFields);
            guessNotMappingName(ctField, OneToOne.class, skipFields);
        }
    }

    private void guessNotMappingName(CtField ctField, Class clzz, List<String> skipFields) {
        if (ctField.hasAnnotation(clzz)) {
            Method mappedBy = null;
            try {
                Object wow = ctField.getAnnotation(clzz);
                mappedBy = wow.getClass().getMethod("mappedBy");
                String value = (String) mappedBy.invoke(wow);
                if (value == null || value.isEmpty()) {
                    skipFields.add(ctField.getName() + "_id");
                }
            } catch (Exception e) {
                if (mappedBy == null) {
                    skipFields.add(ctField.getName() + "_id");
                }
            }
        }
    }
}
