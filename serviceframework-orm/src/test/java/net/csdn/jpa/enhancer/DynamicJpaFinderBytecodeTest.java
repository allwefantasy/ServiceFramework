package net.csdn.jpa.enhancer;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import net.csdn.common.enhancer.DynamicBytecode;
import net.csdn.jpa.model.JPQL;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class DynamicJpaFinderBytecodeTest {

    private final int index;
    private final String fieldName;
    private final String capitalized;

    public DynamicJpaFinderBytecodeTest(int index, String fieldName, String capitalized) {
        this.index = index;
        this.fieldName = fieldName;
        this.capitalized = capitalized;
    }

    @Parameterized.Parameters(name = "{index}: {1}")
    public static Collection<Object[]> cases() {
        String[] roots = new String[]{
                "name", "status", "createdAt", "ownerId", "categoryId",
                "title", "published", "score", "tenantId", "versionNo"
        };
        List<Object[]> rows = new ArrayList<Object[]>();
        for (int i = 0; i < 130; i++) {
            String root = roots[i % roots.length];
            String field = root + i;
            rows.add(new Object[]{i, field, root.substring(0, 1).toUpperCase() + root.substring(1) + i});
        }
        return rows;
    }

    @Test
    public void addsActiveRecordFinderMethodsToModelBytecode() throws Exception {
        ClassPool pool = classPool();
        CtClass model = pool.makeClass("net.csdn.jpa.enhancer.generated.DynamicJpaModel" + index);
        model.setSuperclass(pool.get("net.csdn.jpa.model.Model"));
        model.addField(CtField.make("private java.lang.String " + fieldName + ";", model));

        int added = DynamicBytecode.addJpaDynamicFinders(model, DynamicBytecode.INSTANCE_FIELD_FILTER);

        assertEquals(5, added);
        assertMethod(model, "findBy" + capitalized, "net.csdn.jpa.model.JPABase");
        assertMethod(model, "findAllBy" + capitalized, "java.util.List");
        assertMethod(model, "whereBy" + capitalized, "net.csdn.jpa.model.Model$JPAQuery");
        assertMethod(model, "countBy" + capitalized, "long");
        assertMethod(model, "deleteBy" + capitalized, "int");
        model.detach();
    }

    @Test
    public void generatedFinderTokenIsUnderstoodByJpqlParser() {
        JPQL jpql = new JPQL(null);
        assertEquals(fieldName + " = ?1", jpql.findByToJPQL("by" + capitalized));
    }

    private static ClassPool classPool() {
        ClassPool pool = new ClassPool(true);
        pool.appendClassPath(new LoaderClassPath(DynamicJpaFinderBytecodeTest.class.getClassLoader()));
        return pool;
    }

    private static void assertMethod(CtClass model, String name, String returnType) throws Exception {
        CtMethod method = model.getDeclaredMethod(name, new CtClass[]{model.getClassPool().get("java.lang.Object")});
        assertEquals(returnType, method.getReturnType().getName());
    }
}
