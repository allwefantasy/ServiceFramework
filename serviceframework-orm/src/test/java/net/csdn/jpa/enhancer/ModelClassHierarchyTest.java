package net.csdn.jpa.enhancer;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtNewConstructor;
import javassist.LoaderClassPath;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.StringMemberValue;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancerHelper;
import net.csdn.jpa.model.Model;
import org.junit.Test;

import java.io.ByteArrayInputStream;

import javax.persistence.Column;
import java.util.List;

import static net.csdn.common.collections.WowCollections.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ModelClassHierarchyTest {

    @Test
    public void walksDirectParentsAndShadowsFieldsWithoutLoadingTargets() throws Exception {
        ClassPool pool = classPool();
        CtClass level1 = pool.makeClass("net.csdn.jpa.enhancer.hier.Level1");
        level1.setSuperclass(pool.get(Model.class.getName()));
        level1.addField(CtField.make("private java.lang.String kind;", level1));
        level1.addField(CtField.make("private java.lang.String rootOnly;", level1));

        CtClass level2 = pool.makeClass("net.csdn.jpa.enhancer.hier.Level2");
        level2.setSuperclass(level1);
        level2.addField(CtField.make("private java.lang.Integer kind;", level2));
        level2.addField(CtField.make("private java.lang.String middleOnly;", level2));

        CtClass level3 = pool.makeClass("net.csdn.jpa.enhancer.hier.Level3");
        level3.setSuperclass(level2);
        level3.addField(CtField.make("private java.lang.String kind;", level3));

        List<CtField> fields = ModelClass.fields(level3, null);
        CtField kind = find(fields, "kind");
        assertEquals(level3.getName(), kind.getDeclaringClass().getName());
        assertEquals("java.lang.String", kind.getType().getName());
        assertEquals(level2.getName(), find(fields, "middleOnly").getDeclaringClass().getName());
        assertEquals(level1.getName(), find(fields, "rootOnly").getDeclaringClass().getName());
        assertFalse(names(fields).contains("parent$_validate_info"));

        List<CtField> shadowed = ModelClass.fields(level3, new ModelClass.FieldFilter() {
            @Override
            public boolean filter(CtField field) {
                return !"kind".equals(field.getName()) || field.getDeclaringClass() != level3;
            }
        });
        assertFalse(names(shadowed).contains("kind"));
        assertTrue(names(shadowed).contains("middleOnly"));

        final StringBuilder visited = new StringBuilder();
        ModelClass.iterateSuperClass(level3, new ModelClass.SuperClassIterator() {
            @Override
            public void iterate(CtClass ctClass) {
                if (visited.length() > 0) {
                    visited.append(',');
                }
                visited.append(ctClass.getSimpleName());
            }
        });
        assertEquals("Level2,Level1", visited.toString());

        ModelClass.Tree tree = ModelClass.buildTree(java.util.Arrays.asList(level1, level3, level2));
        assertEquals(1, tree.roots().size());
        ModelClass root = tree.roots().get(0);
        assertEquals(level1.getName(), root.originClass.getName());
        assertNull(root.parent());
        assertEquals(1, root.children().size());
        ModelClass middle = root.children().get(0);
        assertEquals(level2.getName(), middle.originClass.getName());
        assertEquals(level1.getName(), middle.parent().originClass.getName());
        assertEquals(1, middle.children().size());
        ModelClass leaf = middle.children().get(0);
        assertEquals(level3.getName(), leaf.originClass.getName());
        assertEquals(level2.getName(), leaf.parent().originClass.getName());
        assertFalse(ModelClass.isModelSubclass(pool.get(Model.class.getName())));
        assertTrue(ModelClass.isModelSubclass(level3));

        try {
            Class.forName("net.csdn.jpa.enhancer.hier.Level3");
            fail("hierarchy walk defined a JVM class");
        } catch (ClassNotFoundException expected) {
            assertEquals("net.csdn.jpa.enhancer.hier.Level3", expected.getMessage());
        }
    }

    @Test
    public void missingParentOfAModelFailsInsteadOfBeingSkipped() throws Exception {
        ClassPool source = classPool();
        CtClass parent = source.makeClass("net.csdn.jpa.enhancer.hier.MissingParent");
        parent.setSuperclass(source.get(Model.class.getName()));
        parent.addConstructor(CtNewConstructor.defaultConstructor(parent));
        CtClass child = source.makeClass("net.csdn.jpa.enhancer.hier.OrphanModel");
        child.setSuperclass(parent);
        child.addConstructor(CtNewConstructor.defaultConstructor(child));
        byte[] bytes = child.toBytecode();

        ClassPool isolated = classPool();
        CtClass loaded = isolated.makeClass(new ByteArrayInputStream(bytes));
        CtClass plain = isolated.makeClass("net.csdn.jpa.enhancer.hier.PlainHelper");
        assertFalse(ModelClass.isModelSubclass(plain));
        assertFalse(ModelClass.isModelSubclass(isolated.get(Model.class.getName())));
        try {
            ModelClass.isModelSubclass(loaded);
            fail("missing superclass was treated as a non-model");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.ENHANCEMENT, failure.getCategory());
            assertEquals("hierarchy", failure.getPhase());
            assertEquals(loaded.getName(), failure.getClassName());
            assertTrue(failure.getDetail().contains(parent.getName()));
            assertTrue(failure.getCause() instanceof javassist.NotFoundException);
        }
    }

    @Test
    public void copiesInstanceFieldsIntoTheTargetConstantPool() throws Exception {
        ClassPool pool = classPool();
        CtClass source = pool.makeClass("net.csdn.jpa.enhancer.hier.CopySource");
        CtClass target = pool.makeClass("net.csdn.jpa.enhancer.hier.CopyTarget");
        CtField field = CtField.make("private java.lang.String note;", source);
        ConstPool constPool = source.getClassFile().getConstPool();
        EnhancerHelper.createAnnotation(field, Column.class, map(
                "name", new StringMemberValue("note_col", constPool)
        ));
        source.addField(field);
        source.addField(CtField.make("public static java.util.Map parent$_copied;", source));

        EntityEnhancer.copyFieldsToSubclass(source, target);

        CtField copied = target.getDeclaredField("note");
        Column column = (Column) copied.getAnnotation(Column.class);
        assertEquals("note_col", column.name());
        try {
            target.getDeclaredField("parent$_copied");
            fail("static parent field was copied with the instance fields");
        } catch (javassist.NotFoundException expected) {
            assertTrue(expected.getMessage() == null || expected.getMessage().contains("parent$_copied"));
        }
    }

    private static ClassPool classPool() {
        ClassPool pool = new ClassPool(true);
        pool.appendClassPath(new LoaderClassPath(ModelClassHierarchyTest.class.getClassLoader()));
        return pool;
    }

    private static CtField find(List<CtField> fields, String name) {
        for (int i = 0; i < fields.size(); i++) {
            if (name.equals(fields.get(i).getName())) {
                return fields.get(i);
            }
        }
        fail("missing field " + name);
        return null;
    }

    private static java.util.Set<String> names(List<CtField> fields) {
        java.util.Set<String> names = new java.util.LinkedHashSet<String>();
        for (int i = 0; i < fields.size(); i++) {
            names.add(fields.get(i).getName());
        }
        return names;
    }
}
