package net.csdn.common.enhancer;

import javassist.CtClass;
import javassist.CtMethod;
import net.csdn.common.enhancer.fixture.ServiceFrameworkPackageAnchor;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EnhancementDiagnosticsTest {

    @Test
    public void recordsAddedRewrittenAndRemovedMethodsAndTheirRule() throws Exception {
        File dir = directory("sf-diag-methods");
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(dir);
        String digest = sha256("schema-v3".getBytes("UTF-8"));
        diagnostics.noteSafeMetadata("orm-2", digest.toUpperCase());
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader(), diagnostics);
        try {
            final CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.Mutated" + System.nanoTime());
            type.addMethod(CtMethod.make("public int value() { return 1; }", type));
            type.addMethod(CtMethod.make("public void gone() {}", type));
            EnhancementRule rule = new EnhancementRule() {
                @Override
                public String id() {
                    return "shape";
                }

                @Override
                public int version() {
                    return 4;
                }

                @Override
                public String applyReason(CtClass matched, EnhancementContext owner) {
                    return "declares-shape";
                }

                @Override
                public boolean matches(CtClass matched, EnhancementContext owner) {
                    return true;
                }

                @Override
                public void apply(CtClass matched, EnhancementContext owner) {
                    try {
                        matched.getDeclaredMethod("value").setBody("{ return 2; }");
                        matched.removeMethod(matched.getDeclaredMethod("gone"));
                        matched.addMethod(CtMethod.make("public void created() {}", matched));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            };
            EnhancementPlan.compile(Collections.singletonList(rule)).apply(type, context);
            EnhancementDiagnostics.MethodOrigin created = diagnostics.originOf(type.getName(), "created()V");
            EnhancementDiagnostics.MethodOrigin rewritten = diagnostics.originOf(type.getName(), "value()I");
            EnhancementDiagnostics.MethodOrigin removed = diagnostics.originOf(type.getName(), "gone()V");
            assertEquals(EnhancementDiagnostics.MethodChange.ADDED, created.change());
            assertEquals("shape", created.ruleId());
            assertEquals(4, created.ruleVersion());
            assertEquals("apply", created.phase());
            assertEquals(EnhancementDiagnostics.MethodChange.REWRITTEN, rewritten.change());
            assertEquals("shape", rewritten.ruleId());
            assertEquals(EnhancementDiagnostics.MethodChange.REMOVED, removed.change());
            assertEquals(1, diagnostics.methodHistory(type.getName(), "value()I").size());
            EnhancementDiagnostics.Event apply = findPhase(diagnostics, "apply");
            assertEquals(0, apply.methodDelta());
            assertEquals("declares-shape", apply.reason());
            assertEquals("orm-2", apply.configVersion());
            assertEquals(digest, apply.schemaDigest());
            assertEquals(ClassDefiner.JAVA8_MAJOR, apply.java8Target());
            assertEquals(System.getProperty("java.specification.version"), apply.jdk());
            assertTrue(apply.loader().contains(String.valueOf(System.identityHashCode(context.targetLoader()))));
            assertTrue(apply.originalSha256().length() == 64);
            context.close();
            String report = read(new File(dir, "report.txt"));
            assertTrue(report.contains("rule=shape"));
            assertTrue(report.contains("reason=declares-shape"));
            assertTrue(report.contains("configVersion=orm-2"));
            assertTrue(report.contains("java8Target=52"));
            assertTrue(report.contains("created()V"));
            assertFalse(new File(dir, "sources").exists());
            assertFalse(new File(dir, "original").exists());
            assertFalse(new File(dir, "enhanced").exists());
        } finally {
            if (!context.isClosed()) {
                context.close();
            }
        }
    }

    @Test
    public void skipReasonIsRecordedAndUnmatchedRulesDoNotAskForAffectedClasses() throws Exception {
        File dir = directory("sf-diag-skip");
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(dir);
        final int[] skipCalls = new int[1];
        final int[] affectedOnSkip = new int[1];
        final int[] affectedOnApply = new int[1];
        EnhancementRule skipped = new EnhancementRule() {
            @Override
            public String id() {
                return "skipped";
            }

            @Override
            public int version() {
                return 3;
            }

            @Override
            public boolean matches(CtClass type, EnhancementContext context) {
                return false;
            }

            @Override
            public String skipReason(CtClass type, EnhancementContext context) {
                skipCalls[0]++;
                return "not-an-entity";
            }

            @Override
            public List<CtClass> affectedClasses(CtClass type, EnhancementContext context) {
                affectedOnSkip[0]++;
                return Collections.singletonList(type);
            }

            @Override
            public void apply(CtClass type, EnhancementContext context) {
                fail("skipped apply");
            }
        };
        EnhancementRule applied = new EnhancementRule() {
            @Override
            public String id() {
                return "applied";
            }

            @Override
            public boolean matches(CtClass type, EnhancementContext context) {
                return true;
            }

            @Override
            public List<CtClass> affectedClasses(CtClass type, EnhancementContext context) {
                affectedOnApply[0]++;
                return Collections.singletonList(type);
            }

            @Override
            public void apply(CtClass type, EnhancementContext context) {
            }
        };
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader(), diagnostics);
        try {
            EnhancementPlan.compile(Arrays.asList(skipped, applied)).apply(
                    context.makeClass("net.csdn.common.enhancer.fixture.SkipReason" + System.nanoTime()),
                    context);
            EnhancementDiagnostics.Event skip = findRule(diagnostics, "skip", "skipped");
            assertEquals("not-an-entity", skip.reason());
            assertEquals(3, skip.ruleVersion());
            assertEquals(1, skipCalls[0]);
            assertEquals(0, affectedOnSkip[0]);
            assertEquals(1, affectedOnApply[0]);
        } finally {
            context.close();
        }
    }

    @Test
    public void failureKeepsPhaseAndRedactsSecrets() throws Exception {
        File dir = directory("sf-diag-fail");
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(dir);
        EnhancementRule rule = new EnhancementRule() {
            @Override
            public String id() {
                return "broken";
            }

            @Override
            public boolean matches(CtClass type, EnhancementContext context) {
                return true;
            }

            @Override
            public void apply(CtClass type, EnhancementContext context) {
                throw new IllegalStateException("jdbc:mysql://root:hunter2@localhost/app password=hunter2");
            }
        };
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader(), diagnostics);
        try {
            try {
                EnhancementPlan.compile(Collections.singletonList(rule)).apply(
                        context.makeClass("net.csdn.common.enhancer.fixture.SecretFail" + System.nanoTime()),
                        context);
                fail("apply");
            } catch (EnhancementFailure failure) {
                assertEquals("apply", failure.getPhase());
                assertEquals("broken", failure.getRuleId());
            }
            EnhancementDiagnostics.Event event = findRule(diagnostics, "apply", "broken");
            assertTrue(event.failure().contains("[redacted]"));
            assertFalse(event.failure().contains("hunter2"));
            assertFalse(event.failure().contains("jdbc:"));
            context.close();
            String report = read(new File(dir, "report.txt"));
            assertTrue(report.contains("[redacted]"));
            assertFalse(report.contains("hunter2"));
            assertFalse(report.contains("jdbc:"));
        } finally {
            if (!context.isClosed()) {
                context.close();
            }
        }
    }

    @Test
    public void oneRuleEditingTwoClassesFreezesBothOriginalsBeforeTheEdit() throws Exception {
        File dir = directory("sf-diag-batch");
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(dir);
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader(), diagnostics);
        final List<String> captured = new ArrayList<String>();
        final List<String> after = new ArrayList<String>();
        try {
            final CtClass first = context.makeClass("net.csdn.common.enhancer.fixture.BatchA" + System.nanoTime());
            final CtClass second = context.makeClass("net.csdn.common.enhancer.fixture.BatchB" + System.nanoTime());
            final byte[] firstBefore = bytecode(first);
            final byte[] secondBefore = bytecode(second);
            context.addObserver(new EnhancementObserver() {
                @Override
                public void beforeFirstMutation(String className, byte[] originalBytecode) {
                    captured.add(className);
                    if (className.equals(second.getName())) {
                        assertEquals(sha256(secondBefore), sha256(originalBytecode));
                    }
                }

                @Override
                public void afterRule(String className, String ruleId, int version, List<EnhancementDiagnostics.MethodChange> changes) {
                    after.add(className + ":" + changes.size());
                }
            });
            EnhancementRule rule = new EnhancementRule() {
                @Override
                public String id() {
                    return "batch";
                }

                @Override
                public int version() {
                    return 6;
                }

                @Override
                public boolean matches(CtClass type, EnhancementContext owner) {
                    return true;
                }

                @Override
                public List<CtClass> affectedClasses(CtClass type, EnhancementContext owner) {
                    return Arrays.asList(first, second);
                }

                @Override
                public void apply(CtClass type, EnhancementContext owner) {
                    try {
                        first.addMethod(CtMethod.make("public void firstMarked() {}", first));
                        second.addMethod(CtMethod.make("public void secondMarked() {}", second));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
            };
            EnhancementPlan plan = EnhancementPlan.compile(Collections.singletonList(rule));
            plan.apply(first, context);
            assertEquals(sha256(firstBefore), diagnostics.originalHash(first.getName()));
            assertEquals(sha256(secondBefore), diagnostics.originalHash(second.getName()));
            assertFalse(sha256(secondBefore).equals(sha256(bytecode(second))));
            EnhancementDiagnostics.MethodOrigin secondOrigin = diagnostics.originOf(second.getName(), "secondMarked()V");
            assertNotNull(secondOrigin);
            assertEquals("batch", secondOrigin.ruleId());
            assertEquals(6, secondOrigin.ruleVersion());
            assertEquals(EnhancementDiagnostics.MethodChange.ADDED, secondOrigin.change());
            assertEquals("batch", diagnostics.originOf(first.getName(), "firstMarked()V").ruleId());
            assertEquals(1, context.executions().size());
            assertTrue(captured.contains(first.getName()));
            assertTrue(captured.contains(second.getName()));
            assertTrue(after.contains(second.getName() + ":1"));
            assertEquals(2, diagnostics.bytecodeReads());
            assertEquals(4, diagnostics.methodInspections());
        } finally {
            context.close();
        }
    }

    @Test
    public void metadataRejectsRawConfigurationAndDoesNotEchoIt() throws Exception {
        File dir = directory("sf-diag-meta");
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(dir);
        try {
            diagnostics.noteSafeMetadata("jdbc:mysql://root:password@localhost/app", null);
            fail("jdbc");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
            assertFalse(failure.getMessage().contains("jdbc"));
            assertFalse(failure.getMessage().contains("password"));
        }
        try {
            diagnostics.noteSafeMetadata("datasource.password=secret", "not-a-digest");
            fail("dump");
        } catch (EnhancementFailure failure) {
            assertFalse(failure.getMessage().contains("secret"));
            assertFalse(failure.getMessage().contains("not-a-digest"));
        }
        assertNull(diagnostics.configVersion());
        assertNull(diagnostics.schemaDigest());
        EnhancementDiagnostics disabled = EnhancementDiagnostics.disabled();
        disabled.noteSafeMetadata("jdbc:mysql://root:password@localhost/app", "raw schema dump");
        assertEquals(0, disabled.hashComputations());
        assertNull(disabled.configVersion());
        String digest = sha256("schema".getBytes("UTF-8"));
        diagnostics.noteSafeMetadata("mongo-1", digest);
        assertEquals("mongo-1", diagnostics.configVersion());
        assertEquals(digest, diagnostics.schemaDigest());
    }

    @Test
    public void disabledDiagnosticsDoNoBytecodeHashMethodOrDiskWork() throws Exception {
        File dir = directory("sf-diag-quiet");
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.create(false, dir);
        diagnostics.setEmitGeneratedSource(true);
        diagnostics.setEmitClassFiles(true);
        final int[] extra = new int[1];
        EnhancementRule rule = new EnhancementRule() {
            @Override
            public String id() {
                return "quiet";
            }

            @Override
            public boolean matches(CtClass type, EnhancementContext context) {
                return true;
            }

            @Override
            public String applyReason(CtClass type, EnhancementContext context) {
                extra[0]++;
                return "should-not-run";
            }

            @Override
            public String skipReason(CtClass type, EnhancementContext context) {
                extra[0]++;
                return "should-not-run";
            }

            @Override
            public List<CtClass> affectedClasses(CtClass type, EnhancementContext context) {
                extra[0]++;
                return Collections.singletonList(type);
            }

            @Override
            public void apply(CtClass type, EnhancementContext context) {
                try {
                    type.addMethod(CtMethod.make("public void marked() {}", type));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        EnhancementContext context = EnhancementContext.open(
                ServiceFrameworkPackageAnchor.class.getClassLoader(), diagnostics);
        try {
            context.classDefiner().registerAnchor(ServiceFrameworkPackageAnchor.class);
            CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.Quiet" + System.nanoTime());
            diagnostics.emitSource(type.getName(), "GENERATED_SOURCE_MARKER");
            EnhancementPlan.compile(Collections.singletonList(rule)).apply(type, context);
            context.define(type);
            type.getDeclaredMethod("marked");
            assertEquals(0, extra[0]);
            assertEquals(0, diagnostics.hashComputations());
            assertEquals(0, diagnostics.bytecodeReads());
            assertEquals(0, diagnostics.methodInspections());
            assertEquals(0, diagnostics.diskWrites());
            assertTrue(diagnostics.events().isEmpty());
        } finally {
            context.close();
        }
        assertEquals(0, diagnostics.diskWrites());
        assertEquals(0, fileCount(dir));
    }

    @Test
    public void classAndSourceArtifactsAreWrittenOnlyWhenEmitIsEnabled() throws Exception {
        File quietDir = directory("sf-diag-no-emit");
        EnhancementDiagnostics quiet = EnhancementDiagnostics.enabled(quietDir);
        assertFalse(quiet.emitGeneratedSource());
        assertFalse(quiet.emitClassFiles());
        EnhancementContext quietContext = EnhancementContext.open(
                ServiceFrameworkPackageAnchor.class.getClassLoader(), quiet);
        try {
            quietContext.classDefiner().registerAnchor(ServiceFrameworkPackageAnchor.class);
            CtClass type = instrument(quietContext, "plain");
            quiet.emitSource(type.getName(), "SHOULD_NOT_LAND");
            quietContext.define(type);
        } finally {
            quietContext.close();
        }
        assertTrue(new File(quietDir, "report.txt").isFile());
        assertFalse(new File(quietDir, "sources").exists());
        assertFalse(new File(quietDir, "original").exists());
        assertFalse(new File(quietDir, "enhanced").exists());
        assertFalse(read(new File(quietDir, "report.txt")).contains("SHOULD_NOT_LAND"));

        File loudDir = directory("sf-diag-emit");
        EnhancementDiagnostics loud = EnhancementDiagnostics.enabled(loudDir);
        loud.setEmitGeneratedSource(true);
        loud.setEmitClassFiles(true);
        EnhancementContext loudContext = EnhancementContext.open(
                ServiceFrameworkPackageAnchor.class.getClassLoader(), loud);
        String className;
        try {
            loudContext.classDefiner().registerAnchor(ServiceFrameworkPackageAnchor.class);
            CtClass type = instrument(loudContext, "plain");
            className = type.getName();
            loud.emitSource(className, "GENERATED_SOURCE_MARKER");
            loudContext.define(type);
        } finally {
            loudContext.close();
        }
        String source = read(new File(loudDir, "sources").listFiles()[0]);
        assertTrue(source.contains("GENERATED_SOURCE_MARKER"));
        File original = new File(loudDir, "original/" + className + ".class");
        File enhanced = new File(loudDir, "enhanced/" + className + ".class");
        assertTrue(original.isFile());
        assertTrue(enhanced.isFile());
        assertFalse(Arrays.equals(readBytes(original), readBytes(enhanced)));
        assertTrue(loud.diskWrites() >= 3);
    }

    @Test
    public void failedFlushCanBeRetriedWithoutDuplicatingEvents() throws Exception {
        File root = directory("sf-diag-retry");
        File blocker = new File(root, "out");
        writeBytes(blocker, new byte[] {1});
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(blocker);
        diagnostics.recordApply("demo.Retry", "marked", 2, 1, 8L);
        try {
            diagnostics.flush();
            fail("file output");
        } catch (EnhancementFailure failure) {
            assertEquals("diagnostics", failure.getPhase());
            assertFalse(diagnostics.flushed());
        }
        assertEquals(1, diagnostics.events().size());
        assertTrue(blocker.delete());
        assertTrue(blocker.mkdirs());
        diagnostics.setEmitGeneratedSource(true);
        diagnostics.emitSource("demo.Retry", "GENERATED_ONCE");
        assertTrue(new File(blocker, "sources").createNewFile());
        try {
            diagnostics.flush();
            fail("sources blocked");
        } catch (EnhancementFailure failure) {
            assertFalse(diagnostics.flushed());
        }
        assertEquals(1, eventLines(new File(blocker, "report.txt")));
        assertTrue(new File(blocker, "sources").delete());
        diagnostics.flush();
        assertTrue(diagnostics.flushed());
        assertEquals(1, eventLines(new File(blocker, "report.txt")));
        String source = read(new File(blocker, "sources").listFiles()[0]);
        assertEquals("GENERATED_ONCE", source);
        long writes = diagnostics.diskWrites();
        diagnostics.flush();
        assertEquals(writes, diagnostics.diskWrites());
        assertEquals(1, eventLines(new File(blocker, "report.txt")));
    }

    @Test
    public void closeKeepsTheResourceFailureAndAFailedFlush() throws Exception {
        File root = directory("sf-diag-close");
        File blocker = new File(root, "blocked");
        writeBytes(blocker, new byte[] {1});
        final EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(blocker);
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader(), diagnostics);
        context.track(new java.io.Closeable() {
            @Override
            public void close() {
                throw new IllegalStateException("close-resource");
            }
        });
        CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.CloseDiag" + System.nanoTime());
        EnhancementPlan.compile(Collections.singletonList(new EnhancementRule() {
            @Override
            public String id() {
                return "marked";
            }

            @Override
            public boolean matches(CtClass matched, EnhancementContext owner) {
                return true;
            }

            @Override
            public void apply(CtClass matched, EnhancementContext owner) {
            }
        })).apply(type, context);
        try {
            context.close();
            fail("close");
        } catch (RuntimeException thrown) {
            assertTrue(thrown.getMessage(), thrown.getMessage().contains("close-resource"));
            boolean sawDiagnostics = false;
            Throwable[] suppressed = thrown.getSuppressed();
            for (int i = 0; i < suppressed.length; i++) {
                if (String.valueOf(suppressed[i].getMessage()).contains("diagnostics")) {
                    sawDiagnostics = true;
                }
            }
            assertTrue(sawDiagnostics);
        }
        assertTrue(context.isClosed());
        assertFalse(diagnostics.flushed());
        assertTrue(blocker.delete());
        assertTrue(blocker.mkdirs());
        diagnostics.flush();
        assertTrue(diagnostics.flushed());
        String report = read(new File(blocker, "report.txt"));
        assertEquals(1, eventLines(new File(blocker, "report.txt")));
        assertTrue(report.contains("rule=marked"));
    }

    @Test
    public void nullAffectedClassesFailBeforeTheRuleEdits() throws Exception {
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(directory("sf-diag-null"));
        final CtClass[] edited = new CtClass[1];
        EnhancementRule rule = new EnhancementRule() {
            @Override
            public String id() {
                return "null-set";
            }

            @Override
            public boolean matches(CtClass type, EnhancementContext context) {
                return true;
            }

            @Override
            public List<CtClass> affectedClasses(CtClass type, EnhancementContext context) {
                return null;
            }

            @Override
            public void apply(CtClass type, EnhancementContext context) {
                edited[0] = type;
            }
        };
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader(), diagnostics);
        try {
            CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.NullAffected" + System.nanoTime());
            try {
                EnhancementPlan.compile(Collections.singletonList(rule)).apply(type, context);
                fail("null affected");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
                assertEquals("null-set", failure.getRuleId());
            }
            assertNull(edited[0]);
            assertEquals(0, type.getDeclaredMethods().length);
        } finally {
            context.close();
        }
    }

    private static CtClass instrument(EnhancementContext context, String bodyName) throws Exception {
        final CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.Emit" + bodyName + System.nanoTime());
        EnhancementRule rule = new EnhancementRule() {
            @Override
            public String id() {
                return "marked";
            }

            @Override
            public boolean matches(CtClass matched, EnhancementContext owner) {
                return true;
            }

            @Override
            public void apply(CtClass matched, EnhancementContext owner) {
                try {
                    matched.addMethod(CtMethod.make("public void marked() {}", matched));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        EnhancementPlan.compile(Collections.singletonList(rule)).apply(type, context);
        return type;
    }

    private static EnhancementDiagnostics.Event findPhase(EnhancementDiagnostics diagnostics, String phase) {
        for (int i = 0; i < diagnostics.events().size(); i++) {
            if (phase.equals(diagnostics.events().get(i).phase())) {
                return diagnostics.events().get(i);
            }
        }
        fail(phase);
        return null;
    }

    private static EnhancementDiagnostics.Event findRule(EnhancementDiagnostics diagnostics, String phase, String ruleId) {
        for (int i = 0; i < diagnostics.events().size(); i++) {
            EnhancementDiagnostics.Event event = diagnostics.events().get(i);
            if (phase.equals(event.phase()) && ruleId.equals(event.ruleId())) {
                return event;
            }
        }
        fail(phase + " " + ruleId);
        return null;
    }

    private static byte[] bytecode(CtClass type) throws Exception {
        type.stopPruning(true);
        byte[] bytes = type.toBytecode();
        if (type.isFrozen()) {
            type.defrost();
        }
        return bytes;
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (int i = 0; i < hash.length; i++) {
                int value = hash[i] & 0xff;
                if (value < 16) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static File directory(String prefix) {
        File dir = new File(System.getProperty("java.io.tmpdir"), prefix + "-" + System.nanoTime());
        assertTrue(dir.mkdirs());
        return dir;
    }

    private static int fileCount(File dir) {
        File[] files = dir.listFiles();
        return files == null ? 0 : files.length;
    }

    private static int eventLines(File report) throws Exception {
        String text = read(report);
        int count = 0;
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].startsWith("class=")) {
                count++;
            }
        }
        return count;
    }

    private static String read(File file) throws Exception {
        byte[] bytes = readBytes(file);
        return new String(bytes, "UTF-8");
    }

    private static byte[] readBytes(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    break;
                }
                offset += count;
            }
            return bytes;
        } finally {
            input.close();
        }
    }

    private static void writeBytes(File file, byte[] bytes) throws Exception {
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(bytes);
        } finally {
            output.close();
        }
    }
}
