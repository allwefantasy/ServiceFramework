package net.csdn.common.enhancer;

import javassist.CtClass;
import javassist.CtMethod;
import net.csdn.common.enhancer.fixture.SecondAnchor;
import net.csdn.common.enhancer.fixture.ServiceFrameworkPackageAnchor;
import org.junit.Test;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.ProtectionDomain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ClassDefinerTest {

    @Test
    public void definesThroughConventionalAnchorAndCallsPackagePrivateMethod() throws Exception {
        EnhancementContext context = EnhancementContext.open(ServiceFrameworkPackageAnchor.class.getClassLoader());
        try {
            String name = "net.csdn.common.enhancer.fixture.Probe" + System.nanoTime();
            CtClass type = context.makeClass(name);
            type.addMethod(CtMethod.make(
                    "public static int readToken() { return net.csdn.common.enhancer.fixture.ServiceFrameworkPackageAnchor.token(); }",
                    type));
            Class<?> defined = context.define(type);
            assertEquals(42, ((Integer) defined.getMethod("readToken").invoke(null)).intValue());
            assertSame(ServiceFrameworkPackageAnchor.class.getClassLoader(), defined.getClassLoader());
            assertSame(ServiceFrameworkPackageAnchor.class.getProtectionDomain(), defined.getProtectionDomain());
            assertEquals(ClassDefiner.JAVA8_MAJOR, major(type.toBytecode()));
            assertFalse(name.equals(ServiceFrameworkPackageAnchor.class.getName()));
        } finally {
            context.close();
        }
    }

    @Test
    public void registerAnchorIsIdempotentAndRejectsADifferentAnchor() throws Exception {
        EnhancementContext context = EnhancementContext.open(ServiceFrameworkPackageAnchor.class.getClassLoader());
        try {
            ClassDefiner definer = context.classDefiner();
            definer.registerAnchor(ServiceFrameworkPackageAnchor.class);
            definer.registerAnchor(ServiceFrameworkPackageAnchor.class);
            try {
                definer.registerAnchor(SecondAnchor.class);
                fail("different anchor");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                assertEquals("register-anchor", failure.getPhase());
                assertTrue(failure.getMessage().contains(SecondAnchor.class.getName()));
            }
        } finally {
            context.close();
        }
    }

    @Test
    public void duplicateDefineFailsBeforeDefinition() throws Exception {
        EnhancementContext context = EnhancementContext.open(ServiceFrameworkPackageAnchor.class.getClassLoader());
        try {
            CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.Twice" + System.nanoTime());
            Class<?> defined = context.define(type);
            try {
                context.define(type);
                fail("duplicate");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
                assertEquals("define", failure.getPhase());
                assertEquals(type.getName(), failure.getClassName());
                assertNull(failure.getCause());
            }
            assertSame(defined, Class.forName(type.getName(), false, defined.getClassLoader()));
        } finally {
            context.close();
        }
    }

    @Test
    public void missingAnchorDoesNotDefineTheClass() throws Exception {
        ClassLoader loader = ServiceFrameworkPackageAnchor.class.getClassLoader();
        EnhancementContext context = EnhancementContext.open(loader);
        try {
            String name = "net.csdn.common.enhancer.noanchor.Missing" + System.nanoTime();
            CtClass type = context.makeClass(name);
            try {
                context.define(type);
                fail("missing anchor");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.DEFINITION, failure.getCategory());
                assertEquals(name, failure.getClassName());
                assertTrue(failure.getCause() instanceof ClassNotFoundException);
                assertTrue(failure.getMessage().contains("ServiceFrameworkPackageAnchor"));
            }
            try {
                Class.forName(name, false, loader);
                fail("target was defined");
            } catch (ClassNotFoundException expected) {
                assertEquals(name, expected.getMessage());
            }
        } finally {
            context.close();
        }
    }

    @Test
    public void loaderAndDomainMismatchFailBeforeDefinition() throws Exception {
        ClassLoader parent = ServiceFrameworkPackageAnchor.class.getClassLoader();
        URLClassLoader child = new URLClassLoader(new URL[0], parent);
        EnhancementContext context = EnhancementContext.open(parent);
        try {
            CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.LoaderMismatch" + System.nanoTime());
            try {
                context.classDefiner().define(type, child, ServiceFrameworkPackageAnchor.class.getProtectionDomain());
                fail("loader mismatch");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.DEFINITION, failure.getCategory());
                assertNull(failure.getCause());
                assertTrue(failure.getMessage().contains("loader"));
            }
            CtClass other = context.makeClass("net.csdn.common.enhancer.fixture.DomainMismatch" + System.nanoTime());
            try {
                context.classDefiner().define(other, parent, new ProtectionDomain(null, null));
                fail("domain mismatch");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.DEFINITION, failure.getCategory());
                assertNull(failure.getCause());
                assertTrue(failure.getMessage().contains("protection domain"));
            }
            try {
                Class.forName(type.getName(), false, parent);
                fail("mismatch still defined");
            } catch (ClassNotFoundException expected) {
                // definition did not run
            }
        } finally {
            child.close();
            context.close();
        }
    }

    @Test
    public void preValidationFailuresAreRecordedWithLocatingDetails() throws Exception {
        File dir = new File(System.getProperty("java.io.tmpdir"), "sf-definer-diag-" + System.nanoTime());
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(dir);
        ClassLoader parent = ServiceFrameworkPackageAnchor.class.getClassLoader();
        EnhancementContext context = EnhancementContext.open(parent, diagnostics);
        try {
            CtClass missing = context.makeClass("net.csdn.common.enhancer.noanchor.Missing" + System.nanoTime());
            try {
                context.define(missing);
                fail("missing anchor");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.DEFINITION, failure.getCategory());
                assertEquals(missing.getName(), failure.getClassName());
                assertTrue(failure.getMessage(), failure.getMessage().contains("loader="));
                assertTrue(failure.getMessage(), failure.getMessage().contains("jdk="));
            }
            context.classDefiner().registerAnchor(ServiceFrameworkPackageAnchor.class);
            URLClassLoader child = new URLClassLoader(new URL[0], parent);
            try {
                CtClass mismatch = context.makeClass("net.csdn.common.enhancer.fixture.Mismatch" + System.nanoTime());
                try {
                    context.classDefiner().define(mismatch, child, ServiceFrameworkPackageAnchor.class.getProtectionDomain());
                    fail("loader mismatch");
                } catch (EnhancementFailure failure) {
                    assertEquals(EnhancementFailure.Category.DEFINITION, failure.getCategory());
                    assertTrue(failure.getMessage(), failure.getMessage().contains("anchorLoader="));
                    assertTrue(failure.getMessage(), failure.getMessage().contains("anchorCodeSource="));
                }
            } finally {
                child.close();
            }
            int defineFailures = 0;
            for (int i = 0; i < diagnostics.events().size(); i++) {
                EnhancementDiagnostics.Event event = diagnostics.events().get(i);
                if ("define".equals(event.phase()) && event.failure() != null && event.failure().length() > 0) {
                    defineFailures++;
                }
            }
            assertEquals(2, defineFailures);
        } finally {
            context.close();
        }
    }

    @Test
    public void namedModuleProbeDoesNotTreatClasspathAsNamed() throws Exception {
        EnhancementContext context = EnhancementContext.open(ServiceFrameworkPackageAnchor.class.getClassLoader());
        try {
            assertFalse(ClassDefiner.isNamedModule(ServiceFrameworkPackageAnchor.class));
            if (ClassDefiner.isNamedModule(String.class)) {
                try {
                    context.classDefiner().registerAnchor(String.class);
                    fail("named module");
                } catch (EnhancementFailure failure) {
                    assertEquals(EnhancementFailure.Category.UNSUPPORTED, failure.getCategory());
                    assertTrue(failure.getMessage().contains("named module"));
                }
            } else {
                assertFalse(ClassDefiner.isNamedModule(String.class));
            }
        } finally {
            context.close();
        }
    }

    @Test
    public void classDefinerBytecodeStaysOnJava8AndDoesNotNameJdk9Types() throws Exception {
        InputStream stream = ClassDefiner.class.getResourceAsStream("ClassDefiner.class");
        byte[] bytes = read(stream);
        stream.close();
        assertTrue(major(bytes) <= ClassDefiner.JAVA8_MAJOR);
        String latin = new String(bytes, "ISO-8859-1");
        assertFalse(latin.contains("java/lang/Module"));
        assertFalse(latin.contains("java/lang/invoke/MethodHandles"));
    }

    @Test
    public void runtimeCreatedClassIsForcedToJava8EvenIfJavassistStampedNewer() throws Exception {
        EnhancementContext context = EnhancementContext.open(ServiceFrameworkPackageAnchor.class.getClassLoader());
        try {
            String name = "net.csdn.common.enhancer.fixture.Stamped" + System.nanoTime();
            CtClass raw = context.classPool().makeClass(name);
            raw.getClassFile().setMajorVersion(55);
            context.track(raw);
            Class<?> defined = context.define(raw);
            assertEquals(ClassDefiner.JAVA8_MAJOR, major(raw.toBytecode()));
            assertSame(context.targetLoader(), defined.getClassLoader());
        } finally {
            context.close();
        }
    }

    @Test
    public void existingBytecodeNewerThanJava8IsRejectedBeforeDefinition() throws Exception {
        File root = new File(System.getProperty("java.io.tmpdir"), "sf-version-" + System.nanoTime());
        File sourceDir = new File(root, "src");
        File classes = new File(root, "classes");
        sourceDir.mkdirs();
        classes.mkdirs();
        String simple = "VersionedOriginal" + System.nanoTime();
        String name = "net.csdn.common.enhancer.fixture." + simple;
        File source = new File(sourceDir, simple + ".java");
        OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(source), "UTF-8");
        try {
            writer.write("package net.csdn.common.enhancer.fixture; public class " + simple + " {}");
        } finally {
            writer.close();
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertTrue("JDK compiler is required", compiler != null);
        assertEquals(0, compiler.run(null, null, null, "-d", classes.getPath(), source.getPath()));
        String relative = name.replace('.', '/') + ".class";
        File classFile = new File(classes, relative);
        byte[] bytes = read(new java.io.FileInputStream(classFile));
        bytes[6] = 0;
        bytes[7] = 61;
        FileOutputStream patched = new FileOutputStream(classFile);
        try {
            patched.write(bytes);
        } finally {
            patched.close();
        }
        EnhancementContext context = EnhancementContext.open(ServiceFrameworkPackageAnchor.class.getClassLoader());
        try {
            context.classPool().insertClassPath(classes.getPath());
            CtClass type = context.get(name);
            assertTrue(type.getClassFile().getMajorVersion() > ClassDefiner.JAVA8_MAJOR);
            try {
                context.define(type);
                fail("newer bytecode");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.DEFINITION, failure.getCategory());
                assertNull(failure.getCause());
                assertTrue(failure.getMessage().contains("52"));
            }
            try {
                Class.forName(name, false, context.targetLoader());
                fail("newer class was defined");
            } catch (ClassNotFoundException expected) {
                // rejected before defineClass
            }
        } finally {
            context.close();
        }
    }

    private static int major(byte[] bytes) {
        return ((bytes[6] & 0xff) << 8) | (bytes[7] & 0xff);
    }

    private static byte[] read(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) >= 0) {
            output.write(buffer, 0, count);
        }
        input.close();
        return output.toByteArray();
    }
}
