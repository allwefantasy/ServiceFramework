package net.csdn.jpa.enhancer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import javassist.*;
import net.csdn.annotation.association.NotMapping;
import net.csdn.common.Strings;

import javax.persistence.Inheritance;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import java.lang.reflect.Method;
import java.util.*;

import static net.csdn.common.collections.WowCollections.list;

/**
 * User: WilliamZhu
 * Date: 12-8-21
 * Time: 下午8:51
 */
public class ModelClass {


    public static final String MODEL_CLASS_NAME = "net.csdn.jpa.model.Model";
    public static final Map<CtClass, ModelClass> CTModelClasses = Maps.newHashMap();
    public CtClass originClass;
    private List<String> skipFields = list();
    private List<ModelClass> children = Lists.newArrayList();
    private ModelClass parent = null;

    public static List<ModelClass> ROOTS = Lists.newArrayList();

    public ModelClass(CtClass originClass) {
        this.originClass = originClass;
        notMapping(originClass, skipFields);
    }

    public static ModelClass findModelClass(CtClass ctClass) {
        return CTModelClasses.get(ctClass);
    }

    public List<String> notMappings() {
        return skipFields;
    }

    public ModelClass addChild(ModelClass temp) {
        children.add(temp);
        return this;
    }

    public ModelClass parent(ModelClass temp) {
        this.parent = temp;
        return this;
    }

    public ModelClass parent() {
        return parent;
    }

    public boolean isLeafNode() {
        return children.size() == 0;
    }

    public List<ModelClass> findLeafNodes() {
        List<ModelClass> result = new ArrayList();
        innerFindLeaf(this, result);
        return result;
    }

    private void innerFindLeaf(ModelClass modelClass, List<ModelClass> result) {
        for (ModelClass temp : modelClass.children) {
            if (temp.isLeafNode()) {
                result.add(temp);
            } else {
                innerFindLeaf(temp, result);
            }
        }
    }

    public List<ModelClass> children() {
        return children;
    }

    public static List<CtField> fields(CtClass tOriginClass, FieldFilter fieldFilter) {
        Set<CtField> ctFields = Sets.newHashSet();
        for (CtField field : tOriginClass.getDeclaredFields()) {
            if (fieldFilter.filter(field)) {
                ctFields.add(field);
            }
        }
        int count = 0;
        CtClass ctClass;
        try {
            while (!(ctClass = tOriginClass.getSuperclass()).getClass().getName().equals(MODEL_CLASS_NAME)) {
                for (CtField field : ctClass.getDeclaredFields()) {
                    if (fieldFilter.filter(field)) {
                        ctFields.add(field);
                    }
                }
                //make sure no dead loop
                count++;
                if (count > 5) {
                    break;
                }
            }
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
        return Lists.newArrayList(ctFields);
    }


    public interface FieldFilter {
        public boolean filter(CtField field);
    }


    public static CtMethod findTTMethod(CtClass clazz, String methodName, CtClass... paramTypes) {
        CtClass superclass = clazz;
        try {
            return superclass.getDeclaredMethod(methodName, paramTypes);
        } catch (Exception e2) {
            do {
                try {
                    superclass = superclass.getSuperclass();
                    return superclass.getDeclaredMethod(methodName, paramTypes);
                } catch (Exception e) {
                    continue;
                }
            }
            while (superclass != null && !Object.class.getName().equals(superclass.getName()));
        }

        return null;
    }

    public interface MethodFilter {
        public boolean filter(CtMethod method);
    }

    public static List<CtMethod> findTTMethods(MethodFilter filter, CtClass clazz, String methodName) {
        if (filter == null) filter = new MethodFilter() {
            @Override
            public boolean filter(CtMethod method) {
                return true;
            }
        };
        Set<CtMethod> methodSet = new HashSet();
        CtClass superclass = clazz;
        for (CtMethod method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                if (filter.filter(method)) {
                    methodSet.add(method);
                }
            }
        }

        do {
            try {
                superclass = superclass.getSuperclass();
                for (CtMethod method : superclass.getDeclaredMethods()) {
                    if (method.getName().equals(methodName)) {
                        if (filter.filter(method)) {
                            methodSet.add(method);
                        }
                    }
                }
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        }
        while (superclass != null && !Object.class.getName().equals(superclass.getName()));
        return Lists.newArrayList(methodSet);
    }

    public static boolean isInheritance(CtClass ct) {
        try {
            return ct.hasAnnotation(Inheritance.class) || Modifier.isAbstract(ct.getModifiers()) || !ct.getSuperclass().getClass().getName().equals(MODEL_CLASS_NAME);
        } catch (NotFoundException e) {
            return false;
        }
    }

    public static boolean isLeafClass(List<ModelClass> classes, CtClass ct) {
        if (isInheritance(ct)) return false;
        for (ModelClass modelClass : classes) {
            try {
                if (modelClass.originClass.subtypeOf(ct)) {
                    return false;
                }
            } catch (NotFoundException e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    public static void iterateSuperClass(CtClass ctClass, SuperClassIterator superClassIterator) {
        try {
            CtClass temp;
            int count = 0;
            while (!(temp = ctClass.getSuperclass()).getClass().getName().equals(MODEL_CLASS_NAME)) {
                superClassIterator.iterate(temp);
                count++;
                if (count > 5) {
                    break;
                }
            }
        } catch (NotFoundException e) {
            e.printStackTrace();
        }
    }

    public interface SuperClassIterator {
        public void iterate(CtClass ctClass);
    }


    private void notMapping(CtClass ctClass, List<String> skipFields) {
        if (ctClass.hasAnnotation(NotMapping.class)) {
            try {
                NotMapping notMapping = (NotMapping) ctClass.getAnnotation(NotMapping.class);
                for (String str : notMapping.value()) {
                    skipFields.add(Strings.toUnderscoreCase(str));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        autoNotMapping(ctClass, skipFields);
    }

    //自动过滤掉
    private void autoNotMapping(CtClass ctClass, final List<String> skipFields) {

        fields(ctClass, new FieldFilter() {
            @Override
            public boolean filter(CtField field) {
                guessNotMappingName(field, ManyToOne.class, skipFields);
                guessNotMappingName(field, OneToOne.class, skipFields);
                return false;
            }
        });
    }

    private void guessNotMappingName(CtField ctField, Class clzz, List<String> skipFields) {
        if (ctField.hasAnnotation(clzz)) {
            Method mappedBy = null;
            String tablePrefix = Strings.toUnderscoreCase(ctField.getName());
            try {
                Object wow = ctField.getAnnotation(clzz);
                mappedBy = wow.getClass().getMethod("mappedBy");
                String value = (String) mappedBy.invoke(wow);
                if (value == null || value.isEmpty()) {
                    skipFields.add(tablePrefix + "_id");
                    skipFields.add(Strings.toCamelCase(tablePrefix + "_id", false));
                } else {
                    skipFields.add(Strings.toUnderscoreCase(value));
                    skipFields.add(Strings.toCamelCase(value));
                }
            } catch (Exception e) {
                if (mappedBy == null) {
                    skipFields.add(ctField.getName() + "_id");
                    skipFields.add(Strings.toCamelCase(ctField.getName() + "_id", false));
                }
            }
        }
    }
}
