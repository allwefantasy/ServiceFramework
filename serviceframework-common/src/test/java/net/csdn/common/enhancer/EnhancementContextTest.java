package net.csdn.common.enhancer;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtMethod;
import net.csdn.common.enhancer.fixture.ServiceFrameworkPackageAnchor;
import org.junit.Test;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EnhancementContextTest {

    @Test
    public void contextsDoNotShareAttributesPoolsOrScopes() throws Exception {
        EnhancementContext first = EnhancementContext.open(getClass().getClassLoader());
        EnhancementContext second = EnhancementContext.open(getClass().getClassLoader());
        try {
            first.setAttribute("k", "first");
            second.setAttribute("k", "second");
            assertEquals("first", first.getAttribute("k"));
            assertEquals("second", second.getAttribute("k"));
            assertNotSame(first.classPool(), second.classPool());
            assertNull(EnhancementContext.currentOrNull());
            EnhancementContext.Scope outer = first.activate();
            EnhancementContext.Scope inner = second.activate();
            assertSame(second, EnhancementContext.currentOrNull());
            inner.close();
            assertSame(first, EnhancementContext.currentOrNull());
            outer.close();
            assertNull(EnhancementContext.currentOrNull());
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    public void scopesRejectCrossThreadAndOutOfOrderClose() throws Exception {
        final EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        try {
            final EnhancementContext.Scope outer = context.activate();
            final EnhancementContext.Scope inner = context.activate();
            try {
                outer.close();
                fail("out of order");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
                assertTrue(failure.getMessage().contains("out of order"));
            }
            assertSame(context, EnhancementContext.currentOrNull());
            final EnhancementFailure[] cross = new EnhancementFailure[1];
            Thread other = new Thread() {
                @Override
                public void run() {
                    try {
                        inner.close();
                    } catch (EnhancementFailure failure) {
                        cross[0] = failure;
                    }
                }
            };
            other.start();
            other.join();
            assertTrue(cross[0].getMessage().contains("cannot be closed"));
            assertSame(context, EnhancementContext.currentOrNull());
            inner.close();
            outer.close();
            assertNull(EnhancementContext.currentOrNull());
            EnhancementContext.Scope blocking = context.activate();
            try {
                context.close();
                fail("open scope");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
            }
            assertFalse(context.isClosed());
            blocking.close();
            context.close();
            assertTrue(context.isClosed());
            context.close();
            try {
                context.makeClass("net.csdn.common.enhancer.Closed");
                fail("closed");
            } catch (EnhancementFailure failure) {
                assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
            }
        } finally {
            if (!context.isClosed()) {
                context.close();
            }
        }
    }

    @Test
    public void resourcesAndClassesCloseInReverseAndDetachIsNotClassUnload() throws Exception {
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        final List<String> order = new ArrayList<String>();
        ClassPool pool = context.classPool();
        String name = "net.csdn.common.enhancer.fixture.Tracked" + System.nanoTime();
        context.makeClass(name);
        context.track(closer("a", order));
        context.track(closer("b", order));
        context.track(closer("c", order));
        context.close();
        assertEquals(Arrays.asList("c", "b", "a"), order);
        context.close();
        assertEquals(3, order.size());
        assertNull(pool.getOrNull(name));
    }

    @Test
    public void closeReportsAllResourceFailuresAsSuppressed() {
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        context.track(failing("first"));
        context.track(failing("second"));
        try {
            context.close();
            fail("close failures");
        } catch (RuntimeException thrown) {
            assertTrue(thrown.getMessage(), thrown.getMessage().contains("second"));
            assertEquals(1, thrown.getSuppressed().length);
            assertTrue(thrown.getSuppressed()[0].getMessage().contains("first"));
        }
        assertTrue(context.isClosed());
    }

    @Test
    public void closeClearsDefinerStateAndRejectsNewScopes() throws Exception {
        EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        ClassDefiner definer = context.classDefiner();
        definer.registerAnchor(ServiceFrameworkPackageAnchor.class);
        context.makeClass("net.csdn.common.enhancer.fixture.Detach" + System.nanoTime());
        context.close();
        Field anchors = ClassDefiner.class.getDeclaredField("anchors");
        anchors.setAccessible(true);
        assertTrue(((Map) anchors.get(definer)).isEmpty());
        Field defined = ClassDefiner.class.getDeclaredField("definedByLoader");
        defined.setAccessible(true);
        assertTrue(((Map) defined.get(definer)).isEmpty());
        try {
            context.activate();
            fail("closed activate");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
        }
        try {
            context.classDefiner();
            fail("closed definer");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
        }
        assertTrue(context.executions().isEmpty());
    }

    @Test
    public void closeWaitsForScopesOwnedByOtherThreads() throws Exception {
        final EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        final CountDownLatch opened = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final Throwable[] error = new Throwable[1];
        Thread worker = new Thread() {
            @Override
            public void run() {
                try {
                    EnhancementContext.Scope scope = context.activate();
                    opened.countDown();
                    release.await();
                    scope.close();
                } catch (Throwable thrown) {
                    error[0] = thrown;
                }
            }
        };
        worker.start();
        opened.await();
        try {
            context.close();
            fail("open scope on another thread");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
        }
        assertFalse(context.isClosed());
        release.countDown();
        worker.join();
        assertNull(error[0]);
        context.close();
        assertTrue(context.isClosed());
    }

    @Test
    public void shutdownAdmissionRejectsActiveScopesThenBlocksNewOnes() throws Exception {
        final EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        final AtomicInteger closedResources = new AtomicInteger();
        context.track(new Closeable() {
            @Override
            public void close() {
                closedResources.incrementAndGet();
                if (EnhancementContext.currentOrNull() != null) {
                    throw new IllegalStateException("resource closed while a scope was active");
                }
            }
        });
        final CyclicBarrier held = new CyclicBarrier(2);
        final AtomicReference<EnhancementContext.ShutdownAdmission> rejected =
                new AtomicReference<EnhancementContext.ShutdownAdmission>();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread requester = new Thread() {
            @Override
            public void run() {
                try {
                    held.await(5, TimeUnit.SECONDS);
                    rejected.set(context.admitShutdown());
                } catch (Throwable thrown) {
                    error.set(thrown);
                }
            }
        };
        EnhancementContext.Scope scope = context.activate();
        requester.start();
        held.await(5, TimeUnit.SECONDS);
        requester.join(5000);
        assertFalse(requester.isAlive());
        assertNull(error.get());
        assertEquals(EnhancementContext.ShutdownAdmission.REJECTED_ACTIVE, rejected.get());
        assertFalse(context.isClosed());
        assertFalse(context.isShutdownAdmitted());
        assertEquals(0, closedResources.get());
        EnhancementContext.Scope nested = context.activate();
        nested.close();
        scope.close();

        final CyclicBarrier race = new CyclicBarrier(2);
        final AtomicReference<EnhancementContext.ShutdownAdmission> reserved =
                new AtomicReference<EnhancementContext.ShutdownAdmission>();
        final AtomicReference<Throwable> activateFailure = new AtomicReference<Throwable>();
        final AtomicInteger activated = new AtomicInteger();
        Thread activator = new Thread() {
            @Override
            public void run() {
                try {
                    race.await(5, TimeUnit.SECONDS);
                    long deadline = System.currentTimeMillis() + 5000;
                    while (!context.isShutdownAdmitted() && System.currentTimeMillis() < deadline) {
                        Thread.yield();
                    }
                    try {
                        context.activate();
                        activated.incrementAndGet();
                    } catch (RuntimeException thrown) {
                        activateFailure.set(thrown);
                    }
                } catch (Throwable thrown) {
                    error.set(thrown);
                }
            }
        };
        Thread reserver = new Thread() {
            @Override
            public void run() {
                try {
                    race.await(5, TimeUnit.SECONDS);
                    reserved.set(context.admitShutdown());
                } catch (Throwable thrown) {
                    error.compareAndSet(null, thrown);
                }
            }
        };
        activator.start();
        reserver.start();
        activator.join(5000);
        reserver.join(5000);
        assertFalse(activator.isAlive());
        assertFalse(reserver.isAlive());
        assertNull(error.get());
        assertEquals(EnhancementContext.ShutdownAdmission.RESERVED, reserved.get());
        assertTrue(context.isShutdownAdmitted());
        assertFalse(context.isClosed());
        assertEquals(0, activated.get());
        assertTrue(activateFailure.get() instanceof EnhancementFailure);
        assertEquals(0, closedResources.get());
        context.completeClose();
        assertTrue(context.isClosed());
        assertEquals(1, closedResources.get());
        context.completeClose();
        context.close();
        assertEquals(1, closedResources.get());
    }

    @Test
    public void concurrentCloseReleasesResourcesOnce() throws Exception {
        final EnhancementContext context = EnhancementContext.open(getClass().getClassLoader());
        final AtomicInteger closedResources = new AtomicInteger();
        context.track(new Closeable() {
            @Override
            public void close() {
                closedResources.incrementAndGet();
            }
        });
        final CyclicBarrier start = new CyclicBarrier(2);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread[] threads = new Thread[2];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread() {
                @Override
                public void run() {
                    try {
                        start.await(5, TimeUnit.SECONDS);
                        context.close();
                    } catch (Throwable thrown) {
                        error.compareAndSet(null, thrown);
                    }
                }
            };
            threads[i].start();
        }
        for (int i = 0; i < threads.length; i++) {
            threads[i].join(5000);
            assertFalse(threads[i].isAlive());
        }
        assertNull(error.get() == null ? null : error.get().toString(), error.get());
        assertTrue(context.isClosed());
        assertEquals(1, closedResources.get());
        context.close();
        assertEquals(1, closedResources.get());
    }

    @Test
    public void diagnosticsStayOffUntilAskedAndDoNotHashOnClose() throws Exception {
        File disabledDir = new File(System.getProperty("java.io.tmpdir"), "sf-diag-off-" + System.nanoTime());
        disabledDir.mkdirs();
        EnhancementDiagnostics disabled = EnhancementDiagnostics.create(false, disabledDir);
        disabled.setEmitGeneratedSource(true);
        EnhancementContext quiet = EnhancementContext.open(ServiceFrameworkPackageAnchor.class.getClassLoader(), disabled);
        try {
            quiet.classDefiner().registerAnchor(ServiceFrameworkPackageAnchor.class);
            CtClass type = instrument(quiet, "SECRET_TOKEN");
            disabled.emitSource(type.getName(), "GENERATED_SOURCE_MARKER");
            quiet.define(type);
            assertEquals(0, disabled.hashComputations());
            assertTrue(disabled.events().isEmpty());
        } finally {
            quiet.close();
        }
        assertEquals(0, disabled.hashComputations());
        assertEquals(0, fileCount(disabledDir));

        File enabledDir = new File(System.getProperty("java.io.tmpdir"), "sf-diag-on-" + System.nanoTime());
        EnhancementDiagnostics enabled = EnhancementDiagnostics.enabled(enabledDir);
        EnhancementContext loud = EnhancementContext.open(ServiceFrameworkPackageAnchor.class.getClassLoader(), enabled);
        try {
            loud.classDefiner().registerAnchor(ServiceFrameworkPackageAnchor.class);
            CtClass type = instrument(loud, "SECRET_TOKEN");
            Class<?> defined = loud.define(type);
            int hashes = enabled.hashComputations();
            assertTrue(hashes >= 2);
            loud.close();
            assertEquals(hashes, enabled.hashComputations());
            assertTrue(enabled.events().size() >= 2);
            EnhancementDiagnostics.Event apply = enabled.events().get(0);
            assertEquals("apply", apply.phase());
            assertEquals("marked", apply.ruleId());
            assertEquals(8, apply.ruleVersion());
            assertEquals(1, apply.methodDelta());
            assertEquals(64, apply.originalSha256().length());
            assertTrue(apply.elapsedNanos() >= 0);
            EnhancementDiagnostics.Event define = findPhase(enabled, "define");
            assertEquals(64, define.resultSha256().length());
            assertFalse(apply.originalSha256().equals(define.resultSha256()));
            assertTrue(define.loader().contains(String.valueOf(System.identityHashCode(defined.getClassLoader()))));
            String report = read(new File(enabledDir, "report.txt"));
            assertTrue(report.contains("rule=marked"));
            assertFalse(report.contains("SECRET_TOKEN"));
            assertFalse(new File(enabledDir, "sources").exists());
        } finally {
            if (!loud.isClosed()) {
                loud.close();
            }
        }

        File sourceDir = new File(System.getProperty("java.io.tmpdir"), "sf-diag-src-" + System.nanoTime());
        EnhancementDiagnostics sourced = EnhancementDiagnostics.enabled(sourceDir);
        sourced.setEmitGeneratedSource(true);
        EnhancementContext sourceContext = EnhancementContext.open(getClass().getClassLoader(), sourced);
        try {
            CtClass type = sourceContext.makeClass("net.csdn.common.enhancer.fixture.Sourced" + System.nanoTime());
            sourced.emitSource(type.getName(), "GENERATED_SOURCE_MARKER");
            sourceContext.close();
        } finally {
            if (!sourceContext.isClosed()) {
                sourceContext.close();
            }
        }
        String source = read(new File(sourceDir, "sources").listFiles()[0]);
        assertTrue(source.contains("GENERATED_SOURCE_MARKER"));
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

    private static CtClass instrument(EnhancementContext context, final String secret) throws Exception {
        final CtClass type = context.makeClass("net.csdn.common.enhancer.fixture.Diag" + System.nanoTime());
        type.addField(CtField.make("public static final String secret = \"" + secret + "\";", type));
        EnhancementRule rule = new EnhancementRule() {
            @Override
            public String id() {
                return "marked";
            }

            @Override
            public int version() {
                return 8;
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
        EnhancementPlan.compile(Arrays.asList(rule)).apply(type, context);
        return type;
    }

    private static Closeable failing(final String id) {
        return new Closeable() {
            @Override
            public void close() {
                throw new IllegalStateException("close-" + id);
            }
        };
    }

    private static Closeable closer(final String id, final List<String> order) {
        return new Closeable() {
            @Override
            public void close() {
                order.add(id);
            }
        };
    }

    private static int fileCount(File dir) {
        File[] files = dir.listFiles();
        return files == null ? 0 : files.length;
    }

    private static String read(File file) throws Exception {
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
            return new String(bytes, "UTF-8");
        } finally {
            input.close();
        }
    }
}
