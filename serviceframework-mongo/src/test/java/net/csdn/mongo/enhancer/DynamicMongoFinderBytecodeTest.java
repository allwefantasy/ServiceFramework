package net.csdn.mongo.enhancer;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import net.csdn.common.enhancer.DynamicBytecode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class DynamicMongoFinderBytecodeTest {

    private final int index;
    private final String fieldName;
    private final String capitalized;

    public DynamicMongoFinderBytecodeTest(int index, String fieldName, String capitalized) {
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
    public void addsCriteriaFinderMethodsToDocumentBytecode() throws Exception {
        ClassPool pool = classPool();
        CtClass document = pool.makeClass("net.csdn.mongo.enhancer.generated.DynamicMongoDocument" + index);
        document.setSuperclass(pool.get("net.csdn.mongo.Document"));
        document.addField(CtField.make("private java.lang.String " + fieldName + ";", document));

        int added = DynamicBytecode.addMongoDynamicFinders(document, DynamicBytecode.INSTANCE_FIELD_FILTER);

        assertEquals(4, added);
        assertMethod(document, "findBy" + capitalized, "java.lang.Object");
        assertMethod(document, "findAllBy" + capitalized, "java.util.List");
        assertMethod(document, "whereBy" + capitalized, "net.csdn.mongo.Criteria");
        assertMethod(document, "countBy" + capitalized, "int");
        document.detach();
    }

    @Test
    public void skipsDynamicMethodsThatAlreadyExist() throws Exception {
        ClassPool pool = classPool();
        CtClass document = pool.makeClass("net.csdn.mongo.enhancer.generated.DynamicMongoExistingDocument" + index);
        document.setSuperclass(pool.get("net.csdn.mongo.Document"));
        document.addField(CtField.make("private java.lang.String " + fieldName + ";", document));
        document.addMethod(CtMethod.make("public static Object findBy" + capitalized + "(Object value) { return value; }", document));
        document.addMethod(CtMethod.make("public static int countBy" + capitalized + "(Object value) { return 0; }", document));

        int added = DynamicBytecode.addMongoDynamicFinders(document, DynamicBytecode.INSTANCE_FIELD_FILTER);

        assertEquals(2, added);
        assertMethod(document, "findBy" + capitalized, "java.lang.Object");
        assertMethod(document, "findAllBy" + capitalized, "java.util.List");
        assertMethod(document, "whereBy" + capitalized, "net.csdn.mongo.Criteria");
        assertMethod(document, "countBy" + capitalized, "int");
        document.detach();
    }

    private static ClassPool classPool() {
        ClassPool pool = new ClassPool(true);
        pool.appendClassPath(new LoaderClassPath(DynamicMongoFinderBytecodeTest.class.getClassLoader()));
        return pool;
    }

    private static void assertMethod(CtClass document, String name, String returnType) throws Exception {
        CtMethod method = document.getDeclaredMethod(name, new CtClass[]{document.getClassPool().get("java.lang.Object")});
        assertEquals(returnType, method.getReturnType().getName());
    }
}
