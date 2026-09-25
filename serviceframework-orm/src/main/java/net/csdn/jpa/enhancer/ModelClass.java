package net.csdn.jpa.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;
import net.csdn.annotation.association.NotMapping;
import net.csdn.common.Strings;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.jpa.OrmSession;

import javax.persistence.Inheritance;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.csdn.common.collections.WowCollections.list;

/**
 * User: WilliamZhu
 * Date: 12-8-21
 * Time: 下午8:51
 */
public class ModelClass {

    public static final String MODEL_CLASS_NAME = "net.csdn.jpa.model.Model";

    public CtClass originClass;
    private String physicalTable;
    private List<String> skipFields = list();
    private List<ModelClass> children = new ArrayList<ModelClass>();
    private ModelClass parent = null;

    public ModelClass(CtClass originClass) {
        this.originClass = originClass;
        notMapping(originClass, skipFields);
    }

    public static boolean isModelSubclass(CtClass type) {
        if (type == null || MODEL_CLASS_NAME.equals(type.getName())) {
            return false;
        }
        Set<String> seen = new HashSet<String>();
        CtClass current = type;
        while (current != null && !"java.lang.Object".equals(current.getName())) {
            if (!seen.add(current.getName())) {
                throw hierarchyFailure(type.getName(), "cyclic superclass");
            }
            String parentName;
            try {
                parentName = current.getClassFile().getSuperclass();
            } catch (RuntimeException e) {
                throw hierarchyFailure(type.getName(), "superclass was not found", e);
            }
            if (parentName == null || "java.lang.Object".equals(parentName)) {
                return false;
            }
            if (MODEL_CLASS_NAME.equals(parentName)) {
                return true;
            }
            try {
                current = current.getSuperclass();
            } catch (NotFoundException e) {
                throw hierarchyFailure(type.getName(), "superclass " + parentName + " was not found", e);
            }
        }
        return false;
    }

    public static ModelClass findModelClass(CtClass ctClass) {
        if (ctClass == null) {
            return null;
        }
        OrmSession session = OrmSession.currentOrNull();
        if (session == null) {
            return null;
        }
        return session.modelClass(ctClass.getName());
    }

    public static Tree buildTree(List<CtClass> types) {
        List<ModelClass> all = new ArrayList<ModelClass>();
        Map<String, ModelClass> byName = new LinkedHashMap<String, ModelClass>();
        for (int i = 0; i < types.size(); i++) {
            CtClass type = types.get(i);
            if (!isModelSubclass(type)) {
                continue;
            }
            if (byName.containsKey(type.getName())) {
                throw hierarchyFailure(type.getName(), "duplicate model class");
            }
            ModelClass modelClass = new ModelClass(type);
            byName.put(type.getName(), modelClass);
            all.add(modelClass);
        }
        List<ModelClass> roots = new ArrayList<ModelClass>();
        for (int i = 0; i < all.size(); i++) {
            ModelClass modelClass = all.get(i);
            CtClass superclass;
            try {
                superclass = modelClass.originClass.getSuperclass();
            } catch (NotFoundException e) {
                throw hierarchyFailure(modelClass.originClass.getName(), "superclass was not found", e);
            }
            if (superclass == null) {
                throw hierarchyFailure(modelClass.originClass.getName(), "superclass was not found");
            }
            ModelClass parent = byName.get(superclass.getName());
            if (parent == null) {
                if (!MODEL_CLASS_NAME.equals(superclass.getName())) {
                    throw hierarchyFailure(
                            modelClass.originClass.getName(),
                            "superclass " + superclass.getName() + " is not a scanned model and is not Model");
                }
                roots.add(modelClass);
            } else if (parent == modelClass) {
                throw hierarchyFailure(modelClass.originClass.getName(), "cyclic superclass");
            } else {
                parent.addChild(modelClass);
                modelClass.parent(parent);
            }
        }
        parentFirst(roots);
        return new Tree(roots, all);
    }

    public static List<ModelClass> parentFirst(List<ModelClass> roots) {
        List<ModelClass> order = new ArrayList<ModelClass>();
        Set<ModelClass> seen = new HashSet<ModelClass>();
        for (int i = 0; i < roots.size(); i++) {
            walk(roots.get(i), order, seen);
        }
        return order;
    }

    private static void walk(ModelClass node, List<ModelClass> order, Set<ModelClass> seen) {
        if (!seen.add(node)) {
            throw hierarchyFailure(node.originClass.getName(), "cyclic superclass");
        }
        order.add(node);
        List<ModelClass> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            walk(children.get(i), order, seen);
        }
    }

    public String physicalTable() {
        return physicalTable;
    }

    public void physicalTable(String physicalTable) {
        this.physicalTable = physicalTable;
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
        return children.isEmpty();
    }

    public List<ModelClass> findLeafNodes() {
        List<ModelClass> result = new ArrayList<ModelClass>();
        innerFindLeaf(this, result, new HashSet<ModelClass>());
        return result;
    }

    private void innerFindLeaf(ModelClass modelClass, List<ModelClass> result, Set<ModelClass> seen) {
        if (!seen.add(modelClass)) {
            throw hierarchyFailure(modelClass.originClass.getName(), "cyclic superclass");
        }
        for (int i = 0; i < modelClass.children.size(); i++) {
            ModelClass temp = modelClass.children.get(i);
            if (temp.isLeafNode()) {
                result.add(temp);
            } else {
                innerFindLeaf(temp, result, seen);
            }
        }
    }

    public List<ModelClass> children() {
        return children;
    }

    public List<ModelClass> hierarchy() {
        List<ModelClass> order = new ArrayList<ModelClass>();
        walk(this, order, new HashSet<ModelClass>());
        return order;
    }

    /**
     * Declared fields from this class up to, but not including, {@code Model}.
     * A name declared lower in the hierarchy hides the same name above it, even
     * when the filter rejects the lower field. Superclass links advance and cycles fail.
     */
    public static List<CtField> fields(CtClass origin, FieldFilter fieldFilter) {
        LinkedHashMap<String, CtField> accepted = new LinkedHashMap<String, CtField>();
        Set<String> declared = new HashSet<String>();
        Set<String> seenClasses = new HashSet<String>();
        CtClass current = origin;
        while (current != null && !isHierarchyStop(current)) {
            if (!seenClasses.add(current.getName())) {
                throw hierarchyFailure(current.getName(), "cyclic superclass");
            }
            for (CtField field : current.getDeclaredFields()) {
                if (!declared.add(field.getName())) {
                    continue;
                }
                if (fieldFilter == null || fieldFilter.filter(field)) {
                    accepted.put(field.getName(), field);
                }
            }
            try {
                current = current.getSuperclass();
            } catch (NotFoundException e) {
                throw hierarchyFailure(origin.getName(), "superclass was not found", e);
            }
        }
        return new ArrayList<CtField>(accepted.values());
    }

    public static CtField findDeclaredField(CtClass start, String name) {
        Set<String> seen = new HashSet<String>();
        CtClass current = start;
        while (current != null && !isHierarchyStop(current)) {
            if (!seen.add(current.getName())) {
                throw hierarchyFailure(current.getName(), "cyclic superclass");
            }
            try {
                return current.getDeclaredField(name);
            } catch (NotFoundException ignored) {
                // Declared on a further ancestor, or not declared at all.
            }
            try {
                current = current.getSuperclass();
            } catch (NotFoundException e) {
                throw hierarchyFailure(start.getName(), "superclass was not found", e);
            }
        }
        return null;
    }

    public interface FieldFilter {
        boolean filter(CtField field);
    }

    public static CtMethod findTTMethod(CtClass clazz, String methodName, CtClass... paramTypes) {
        CtClass superclass = clazz;
        try {
            return superclass.getDeclaredMethod(methodName, paramTypes);
        } catch (Exception ignored) {
            do {
                try {
                    superclass = superclass.getSuperclass();
                    if (superclass == null) {
                        return null;
                    }
                    return superclass.getDeclaredMethod(methodName, paramTypes);
                } catch (Exception e) {
                    continue;
                }
            }
            while (superclass != null && !"java.lang.Object".equals(superclass.getName()));
        }
        return null;
    }

    public interface MethodFilter {
        boolean filter(CtMethod method);
    }

    public static List<CtMethod> findTTMethods(MethodFilter filter, CtClass clazz, String methodName) {
        if (filter == null) {
            filter = new MethodFilter() {
                @Override
                public boolean filter(CtMethod method) {
                    return true;
                }
            };
        }
        List<CtMethod> methods = new ArrayList<CtMethod>();
        Set<String> seen = new HashSet<String>();
        CtClass current = clazz;
        while (current != null && !"java.lang.Object".equals(current.getName())) {
            if (!seen.add(current.getName())) {
                break;
            }
            for (CtMethod method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && filter.filter(method)) {
                    methods.add(method);
                }
            }
            try {
                current = current.getSuperclass();
            } catch (NotFoundException e) {
                throw hierarchyFailure(clazz.getName(), "superclass was not found", e);
            }
        }
        return methods;
    }

    public static boolean isInheritance(CtClass ct) {
        try {
            if (ct.hasAnnotation(Inheritance.class) || Modifier.isAbstract(ct.getModifiers())) {
                return true;
            }
            CtClass superclass = ct.getSuperclass();
            return superclass != null
                    && !MODEL_CLASS_NAME.equals(superclass.getName())
                    && !"java.lang.Object".equals(superclass.getName());
        } catch (NotFoundException e) {
            return false;
        }
    }

    public static boolean isLeafClass(List<ModelClass> classes, CtClass ct) {
        if (isInheritance(ct)) {
            return false;
        }
        for (int i = 0; i < classes.size(); i++) {
            try {
                if (classes.get(i).originClass.subtypeOf(ct) && classes.get(i).originClass != ct) {
                    return false;
                }
            } catch (NotFoundException e) {
                throw hierarchyFailure(ct.getName(), "subtype check failed", e);
            }
        }
        return true;
    }

    /**
     * Visits each superclass strictly between {@code ctClass} and {@code Model}.
     * The walk advances one superclass at a time and does not load the JVM class.
     */
    public static void iterateSuperClass(CtClass ctClass, SuperClassIterator superClassIterator) {
        Set<String> seen = new HashSet<String>();
        CtClass current = ctClass;
        while (current != null) {
            CtClass parent;
            try {
                parent = current.getSuperclass();
            } catch (NotFoundException e) {
                throw hierarchyFailure(ctClass.getName(), "superclass was not found", e);
            }
            if (parent == null || isHierarchyStop(parent)) {
                return;
            }
            if (!seen.add(parent.getName())) {
                throw hierarchyFailure(parent.getName(), "cyclic superclass");
            }
            superClassIterator.iterate(parent);
            current = parent;
        }
    }

    public interface SuperClassIterator {
        void iterate(CtClass ctClass);
    }

    public static final class Tree {
        private final List<ModelClass> roots;
        private final List<ModelClass> all;

        private Tree(List<ModelClass> roots, List<ModelClass> all) {
            this.roots = roots;
            this.all = all;
        }

        public List<ModelClass> roots() {
            return roots;
        }

        public List<ModelClass> all() {
            return all;
        }
    }

    private static boolean isHierarchyStop(CtClass type) {
        String name = type.getName();
        return MODEL_CLASS_NAME.equals(name) || "java.lang.Object".equals(name);
    }

    private static EnhancementFailure hierarchyFailure(String className, String detail) {
        return hierarchyFailure(className, detail, null);
    }

    private static EnhancementFailure hierarchyFailure(String className, String detail, Exception cause) {
        return new EnhancementFailure(
                EnhancementFailure.Category.ENHANCEMENT,
                className,
                "entity-mapping",
                "hierarchy",
                detail,
                cause);
    }

    private void notMapping(CtClass ctClass, List<String> skipFields) {
        if (ctClass.hasAnnotation(NotMapping.class)) {
            try {
                NotMapping notMapping = (NotMapping) ctClass.getAnnotation(NotMapping.class);
                for (String str : notMapping.value()) {
                    skipFields.add(Strings.toUnderscoreCase(str));
                }
            } catch (Exception e) {
                throw hierarchyFailure(ctClass.getName(), "NotMapping could not be read", e);
            }
        }
        autoNotMapping(ctClass, skipFields);
    }

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
