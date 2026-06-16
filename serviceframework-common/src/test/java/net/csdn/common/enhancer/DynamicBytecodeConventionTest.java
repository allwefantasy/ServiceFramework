package net.csdn.common.enhancer;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class DynamicBytecodeConventionTest {

    private final int index;
    private final String fieldName;
    private final String capitalized;

    public DynamicBytecodeConventionTest(int index, String fieldName, String capitalized) {
        this.index = index;
        this.fieldName = fieldName;
        this.capitalized = capitalized;
    }

    @Parameterized.Parameters(name = "{index}: {1}")
    public static Collection<Object[]> cases() {
        String[] roots = new String[]{
                "name", "statusCode", "createdAt", "updatedAt", "userId",
                "emailAddress", "phoneNumber", "loginCount", "displayName", "tenantKey"
        };
        List<Object[]> rows = new ArrayList<Object[]>();
        for (int i = 0; i < 250; i++) {
            String root = roots[i % roots.length];
            String field = root + i;
            rows.add(new Object[]{i, field, root.substring(0, 1).toUpperCase() + root.substring(1) + i});
        }
        return rows;
    }

    @Test
    public void buildsRailsStyleMethodNames() {
        assertEquals("get" + capitalized, DynamicBytecode.getterName(fieldName));
        assertEquals("set" + capitalized, DynamicBytecode.setterName(fieldName));
        assertEquals("by" + capitalized, DynamicBytecode.jpaQueryToken(fieldName));
        assertEquals("findBy" + capitalized, DynamicBytecode.finderName(fieldName));
        assertEquals("findAllBy" + capitalized, DynamicBytecode.findAllName(fieldName));
        assertEquals("whereBy" + capitalized, DynamicBytecode.whereName(fieldName));
        assertEquals("countBy" + capitalized, DynamicBytecode.countName(fieldName));
        assertEquals("deleteBy" + capitalized, DynamicBytecode.deleteName(fieldName));
    }

    @Test
    public void addsSyntheticBeanAccessors() throws Exception {
        ClassPool pool = new ClassPool(true);
        pool.appendClassPath(new LoaderClassPath(getClass().getClassLoader()));
        CtClass ctClass = pool.makeClass("net.csdn.common.enhancer.generated.DynamicBean" + index);
        ctClass.addField(CtField.make("private java.lang.String " + fieldName + ";", ctClass));

        int added = DynamicBytecode.addBeanAccessors(ctClass, DynamicBytecode.INSTANCE_FIELD_FILTER);

        assertEquals(2, added);
        CtMethod getter = ctClass.getDeclaredMethod("get" + capitalized, new CtClass[0]);
        CtMethod setter = ctClass.getDeclaredMethod("set" + capitalized, new CtClass[]{pool.get("java.lang.String")});
        assertEquals("java.lang.String", getter.getReturnType().getName());
        assertEquals(CtClass.voidType, setter.getReturnType());
        assertTrue(Modifier.isPublic(getter.getModifiers()));
        assertTrue(Modifier.isPublic(setter.getModifiers()));
        assertTrue((getter.getModifiers() & javassist.bytecode.AccessFlag.SYNTHETIC) != 0);
        assertTrue((setter.getModifiers() & javassist.bytecode.AccessFlag.SYNTHETIC) != 0);
        ctClass.detach();
    }
}
