package net.csdn.common.enhancer;

import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.annotation.MemberValue;
import net.csdn.common.enhancer.fixture.FieldMarker;
import net.csdn.common.enhancer.fixture.Samples;
import net.csdn.common.enhancer.fixture.ServiceFrameworkPackageAnchor;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class DynamicBytecodeBehaviorTest {

    @Test
    public void customSetterKeepsBodyAndOtherOverloadsAndSyncsFinalValue() throws Exception {
        EnhancementContext context = open();
        try {
            CtClass type = setterHost(context, true);
            Object first = newInstance(type);
            Method setString = typeOf(first).getMethod("setName", String.class);
            assertFalse(setString.isSynthetic());
            setString.invoke(first, "  Ab ");
            assertEquals("Ab", field(first, "name"));
            assertEquals("Ab", field(first, "persisted"));
            assertEquals(1, ((Integer) field(first, "touches")).intValue());

            Method setObject = typeOf(first).getMethod("setName", Object.class);
            setObject.invoke(first, "z");
            assertEquals("z", field(first, "objectName"));
            assertEquals(11, ((Integer) field(first, "touches")).intValue());
            assertEquals("Ab", field(first, "persisted"));

            Object second = newInstance(type);
            try {
                typeOf(second).getMethod("setName", String.class).invoke(second, new Object[]{null});
                fail("null");
            } catch (InvocationTargetException ex) {
                assertTrue(ex.getCause() instanceof IllegalArgumentException);
            }
            assertNull(field(second, "persisted"));
            assertEquals(0, ((Integer) field(second, "touches")).intValue());
        } finally {
            context.close();
        }
    }

    @Test
    public void synchronizedFinalSetterKeepsContractAndRunsHookUnderMonitor() throws Exception {
        EnhancementContext context = open();
        try {
            CtClass type = context.makeClass(name());
            type.addField(CtField.make("public String name;", type));
            type.addField(CtField.make("public int touches;", type));
            type.addField(CtField.make("public String persisted;", type));
            type.addField(CtField.make("public boolean heldLock;", type));
            CtMethod setter = CtMethod.make(
                    "protected final synchronized void setName(String value) throws java.io.IOException {" +
                            " if (value == null) throw new IllegalArgumentException(\"nope\");" +
                            " this.name = value.trim(); this.touches++; }",
                    type);
            AnnotationsAttribute marker = new AnnotationsAttribute(
                    setter.getMethodInfo().getConstPool(), AnnotationsAttribute.visibleTag);
            EnhancerHelper.createAnnotation(marker, Deprecated.class, new HashMap<String, MemberValue>());
            setter.getMethodInfo().addAttribute(marker);
            type.addMethod(setter);
            DynamicBytecode.addBeanAccessors(type, DynamicBytecode.INSTANCE_FIELD_FILTER, monitorHook(), true);
            Class<?> defined = context.define(type);

            Method method = defined.getDeclaredMethod("setName", String.class);
            assertTrue(Modifier.isProtected(method.getModifiers()));
            assertTrue(Modifier.isFinal(method.getModifiers()));
            assertTrue(Modifier.isSynchronized(method.getModifiers()));
            assertFalse(method.isSynthetic());
            assertEquals(java.io.IOException.class, method.getExceptionTypes()[0]);
            assertNotNull(method.getAnnotation(Deprecated.class));
            int overloads = 0;
            for (Method each : defined.getDeclaredMethods()) {
                if ("setName".equals(each.getName())) {
                    overloads++;
                }
            }
            assertEquals(1, overloads);
            Method helper = defined.getDeclaredMethod("setName$sfHook", String.class);
            assertTrue(Modifier.isPrivate(helper.getModifiers()));
            assertTrue(helper.isSynthetic());

            method.setAccessible(true);
            Object first = defined.getDeclaredConstructor().newInstance();
            method.invoke(first, "  Ab ");
            assertEquals("Ab", defined.getField("name").get(first));
            assertEquals("Ab", defined.getField("persisted").get(first));
            assertTrue(defined.getField("heldLock").getBoolean(first));
            assertEquals(1, defined.getField("touches").getInt(first));

            Object second = defined.getDeclaredConstructor().newInstance();
            try {
                method.invoke(second, new Object[]{null});
                fail("null");
            } catch (InvocationTargetException ex) {
                assertTrue(ex.getCause() instanceof IllegalArgumentException);
            }
            assertNull(defined.getField("persisted").get(second));
            assertFalse(defined.getField("heldLock").getBoolean(second));
        } finally {
            context.close();
        }
    }

    @Test
    public void repeatingSetterInstrumentationConflicts() throws Exception {
        EnhancementContext context = open();
        try {
            CtClass type = context.makeClass(name());
            addSetterFields(type);
            addSetterMethods(type);
            DynamicBytecode.addBeanAccessors(type, DynamicBytecode.INSTANCE_FIELD_FILTER, hook(), true);
            try {
                DynamicBytecode.addBeanAccessors(type, DynamicBytecode.INSTANCE_FIELD_FILTER, hook(), true);
                fail("repeat");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                assertTrue(failure.getMessage().contains("already instrumented"));
            }
            Class<?> defined = context.define(type);
            Object instance = defined.getDeclaredConstructor().newInstance();
            defined.getMethod("setName", String.class).invoke(instance, "  Ab ");
            assertEquals(1, defined.getField("touches").getInt(instance));
            assertEquals("Ab", defined.getField("persisted").get(instance));
        } finally {
            context.close();
        }
    }

    @Test
    public void existingSetterWithoutEnhancementRequestIsKept() throws Exception {
        EnhancementContext context = open();
        try {
            CtClass type = context.makeClass(name());
            addSetterFields(type);
            addSetterMethods(type);
            DynamicBytecode.addBeanAccessors(type, DynamicBytecode.INSTANCE_FIELD_FILTER, hook(), false);
            Class<?> defined = context.define(type);
            Object instance = defined.getDeclaredConstructor().newInstance();
            defined.getMethod("setName", String.class).invoke(instance, "  Ab ");
            assertEquals("Ab", defined.getField("name").get(instance));
            assertNull(defined.getField("persisted").get(instance));
            defined.getMethod("setName", Object.class).invoke(instance, "z");
            assertEquals("z", defined.getField("objectName").get(instance));
        } finally {
            context.close();
        }
    }

    @Test
    public void finalInheritedGetterConflictsInsteadOfReadingSuperclassField() throws Exception {
        assertConflict(Samples.FinalGetterParent.class, "final");
    }

    @Test
    public void openInheritedGetterReadsTheDeclaredField() throws Exception {
        EnhancementContext context = open();
        try {
            CtClass type = subclassWithName(context, Samples.OpenGetterParent.class);
            DynamicBytecode.addBeanAccessors(type, DynamicBytecode.INSTANCE_FIELD_FILTER);
            Class<?> defined = context.define(type);
            Object instance = defined.getDeclaredConstructor().newInstance();
            defined.getMethod("setName", String.class).invoke(instance, "child-value");
            assertEquals("child-value", defined.getMethod("getName").invoke(instance));
        } finally {
            context.close();
        }
    }

    @Test
    public void staticAndReturnTypeConflictsAreReported() throws Exception {
        assertConflict(Samples.StaticGetterParent.class, "static");
        assertConflict(Samples.IntegerGetterParent.class, "return type");
    }

    @Test
    public void copiedStaticConstantsStayIndependentFromNonConstantInitializers() throws Exception {
        EnhancementContext context = open();
        try {
            Class<?> first = copyParentStatic(context);
            Class<?> second = copyParentStatic(context);
            assertEquals("CONST-VALUE", first.getField("parent$_code").get(null));
            assertEquals(7, first.getField("parent$_n").getInt(null));
            assertNull(first.getField("parent$_label").get(null));
            assertEquals(0, first.getField("parent$_counter").getInt(null));
            FieldMarker marker = first.getField("parent$_code").getAnnotation(FieldMarker.class);
            assertNotNull(marker);
            assertEquals("kept", marker.value());
            Field typed = first.getField("parent$_typed");
            assertTrue(typed.getGenericType() instanceof ParameterizedType);
            ParameterizedType generic = (ParameterizedType) typed.getGenericType();
            assertEquals(String.class, generic.getActualTypeArguments()[0]);
            assertEquals(Integer.class, generic.getActualTypeArguments()[1]);

            Map parentMap = Samples.ParentStatic.parent$_map();
            Map firstMap = (Map) first.getMethod("parent$_map").invoke(null);
            Map secondMap = (Map) second.getMethod("parent$_map").invoke(null);
            firstMap.put("only-first", "1");
            assertNotSame(parentMap, firstMap);
            assertNotSame(firstMap, secondMap);
            assertFalse(secondMap.containsKey("only-first"));
            assertFalse(parentMap.containsKey("only-first"));
            assertSame(firstMap, (Map) first.getMethod("parent$_map").invoke(null));
        } finally {
            context.close();
        }
    }

    private static void assertConflict(Class<?> parent, String expected) throws Exception {
        EnhancementContext context = open();
        try {
            CtClass type = subclassWithName(context, parent);
            try {
                DynamicBytecode.addBeanAccessors(type, DynamicBytecode.INSTANCE_FIELD_FILTER);
                fail(expected);
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                assertEquals("bean-accessor", failure.getRuleId());
                assertEquals("enhance", failure.getPhase());
                assertTrue(failure.getMessage(), failure.getMessage().contains(expected));
            }
        } finally {
            context.close();
        }
    }

    private static CtClass subclassWithName(EnhancementContext context, Class<?> parent) throws Exception {
        CtClass type = context.makeClass(name());
        type.setSuperclass(context.get(parent.getName()));
        type.addField(CtField.make("public String name;", type));
        return type;
    }

    private static Class<?> copyParentStatic(EnhancementContext context) throws Exception {
        CtClass source = context.get(Samples.ParentStatic.class.getName());
        CtClass target = context.makeClass(name());
        DynamicBytecode.copyStaticFields(source, target, DynamicBytecode.PARENT_STATIC_FIELD_FILTER);
        DynamicBytecode.copyStaticMethods(source, target, new DynamicBytecode.CtMethodFilter() {
            @Override
            public boolean accept(CtMethod method) {
                return "parent$_map".equals(method.getName());
            }
        });
        return context.define(target);
    }

    private static CtClass setterHost(EnhancementContext context, boolean replace) throws Exception {
        CtClass type = context.makeClass(name());
        addSetterFields(type);
        addSetterMethods(type);
        DynamicBytecode.addBeanAccessors(type, DynamicBytecode.INSTANCE_FIELD_FILTER, hook(), replace);
        context.define(type);
        return type;
    }

    private static void addSetterFields(CtClass type) throws Exception {
        type.addField(CtField.make("public String name;", type));
        type.addField(CtField.make("public int touches;", type));
        type.addField(CtField.make("public String persisted;", type));
        type.addField(CtField.make("public Object objectName;", type));
    }

    private static void addSetterMethods(CtClass type) throws Exception {
        type.addMethod(CtMethod.make(
                "public void setName(String value) { if (value == null) throw new IllegalArgumentException(\"nope\"); this.name = value.trim(); this.touches++; }",
                type));
        type.addMethod(CtMethod.make(
                "public void setName(Object value) { this.objectName = value; this.touches = this.touches + 10; }",
                type));
    }

    private static DynamicBytecode.SetterBody hook() {
        return new DynamicBytecode.SetterBody() {
            @Override
            public String beforeAssignment(CtField field) {
                return "name".equals(field.getName()) ? "persisted = value;" : "";
            }
        };
    }

    private static DynamicBytecode.SetterBody monitorHook() {
        return new DynamicBytecode.SetterBody() {
            @Override
            public String beforeAssignment(CtField field) {
                return "name".equals(field.getName())
                        ? "persisted = value; heldLock = Thread.holdsLock(this);"
                        : "";
            }
        };
    }

    private static Object newInstance(CtClass type) throws Exception {
        Class<?> defined = Class.forName(type.getName(), true, ServiceFrameworkPackageAnchor.class.getClassLoader());
        return defined.getDeclaredConstructor().newInstance();
    }

    private static Class<?> typeOf(Object instance) {
        return instance.getClass();
    }

    private static Object field(Object instance, String name) throws Exception {
        return instance.getClass().getField(name).get(instance);
    }

    private static EnhancementContext open() {
        EnhancementContext context = EnhancementContext.open(ServiceFrameworkPackageAnchor.class.getClassLoader());
        context.classDefiner().registerAnchor(ServiceFrameworkPackageAnchor.class);
        return context;
    }

    private static String name() {
        return "net.csdn.common.enhancer.fixture.Generated" + System.nanoTime();
    }
}
