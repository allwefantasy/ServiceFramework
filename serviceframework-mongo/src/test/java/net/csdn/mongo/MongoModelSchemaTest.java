package net.csdn.mongo;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.StringMemberValue;
import net.csdn.common.enhancer.EnhancementFailure;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MongoModelSchemaTest {

    @Test
    public void digestIsSortedDeclaredModelSchemaAndDropsSecrets() throws Exception {
        CtClass later = type("net.csdn.mongo.schema.Zulu");
        addField(later, "title", "java.lang.String");
        CtClass earlier = type("net.csdn.mongo.schema.Alpha");
        addField(earlier, "name", "java.lang.String");
        addField(earlier, "city", "java.lang.String");
        annotate(earlier, "name",
                marker("net.csdn.mongo.annotations.Transient", earlier),
                marker("net.csdn.mongo.annotations.Validate", earlier));

        CtClass reversed = type("net.csdn.mongo.schema.Alpha");
        addField(reversed, "city", "java.lang.String");
        addField(reversed, "name", "java.lang.String");
        annotate(reversed, "name",
                marker("net.csdn.mongo.annotations.Validate", reversed),
                marker("net.csdn.mongo.annotations.Transient", reversed));
        CtClass zuluAgain = type("net.csdn.mongo.schema.Zulu");
        addField(zuluAgain, "title", "java.lang.String");

        String forward = MongoModelSchema.canonical(Arrays.asList(later, earlier));
        String backward = MongoModelSchema.canonical(Arrays.asList(zuluAgain, reversed));
        assertEquals(forward, backward);
        assertTrue(forward.startsWith("model-schema v1\n"));
        assertTrue(forward.indexOf("class net.csdn.mongo.schema.Alpha") < forward.indexOf("class net.csdn.mongo.schema.Zulu"));
        assertTrue(forward.indexOf("field city java.lang.String -") < forward.indexOf("field name java.lang.String "));
        assertTrue(forward.contains("net.csdn.mongo.annotations.Transient"));
        assertTrue(forward.contains("net.csdn.mongo.annotations.Validate"));
        assertFalse(forward.contains("mongodb://"));
        assertFalse(forward.contains("jdbc:"));
        assertFalse(forward.contains("password"));
        assertFalse(forward.contains("settings"));

        CtClass secret = type("net.csdn.mongo.schema.Alpha");
        addField(secret, "name", "java.lang.String");
        addField(secret, "city", "java.lang.String");
        Annotation leaked = new Annotation("demo.Connection", constPool(secret));
        leaked.addMemberValue("value", new StringMemberValue(
                "mongodb://root:s3cr3t-token@127.0.0.1/admin", constPool(secret)));
        annotate(secret, "name", marker("net.csdn.mongo.annotations.Validate", secret), leaked);
        String redacted = MongoModelSchema.canonical(Arrays.asList(secret, zuluAgain));
        assertTrue(redacted.contains("[redacted]"));
        assertTrue(redacted.contains("net.csdn.mongo.annotations.Validate"));
        assertFalse(redacted.contains("s3cr3t-token"));
        assertFalse(redacted.contains("mongodb://"));
        assertFalse(redacted.contains("jdbc:"));

        String digest = MongoModelSchema.digest(Arrays.asList(later, earlier));
        assertEquals(digest, MongoModelSchema.digest(Arrays.asList(reversed, zuluAgain)));
        assertTrue(digest.matches("[0-9a-f]{64}"));
        assertFalse(digest.equals(MongoModelSchema.digest(Arrays.asList(secret, zuluAgain))));
        assertEquals("mongo-1", MongoModelSchema.VERSION);
    }

    @Test
    public void duplicateClassIsRejectedWithoutASettingsArgument() throws Exception {
        CtClass first = type("net.csdn.mongo.schema.Alpha");
        addField(first, "name", "java.lang.String");
        CtClass second = type("net.csdn.mongo.schema.Alpha");
        addField(second, "name", "java.lang.String");
        try {
            MongoModelSchema.digest(Arrays.asList(first, second));
            fail("duplicate model class was hashed");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
            assertEquals("diagnostics", failure.getPhase());
            assertTrue(failure.getMessage().contains("duplicate"));
            assertFalse(failure.getMessage().contains("password"));
            assertFalse(failure.getMessage().contains("jdbc:"));
        }
    }

    private static CtClass type(String name) throws Exception {
        return new ClassPool(true).makeClass(name);
    }

    private static void addField(CtClass owner, String field, String fieldType) throws Exception {
        owner.addField(new CtField(owner.getClassPool().get(fieldType), field, owner));
    }

    private static void annotate(CtClass owner, String field, Annotation... annotations) throws Exception {
        CtField ctField = owner.getDeclaredField(field);
        ConstPool constPool = ctField.getFieldInfo().getConstPool();
        AnnotationsAttribute attribute = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
        for (int i = 0; i < annotations.length; i++) {
            attribute.addAnnotation(annotations[i]);
        }
        ctField.getFieldInfo().addAttribute(attribute);
    }

    private static Annotation marker(String typeName, CtClass owner) {
        return new Annotation(typeName, constPool(owner));
    }

    private static ConstPool constPool(CtClass type) {
        return type.getClassFile().getConstPool();
    }
}
