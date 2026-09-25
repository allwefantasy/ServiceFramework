package net.csdn.bootstrap.lifecycle;

import com.google.inject.Injector;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.Bootstrap;
import net.csdn.bootstrap.extension.BuiltinExtensions;
import net.csdn.bootstrap.lifecycle.ext.LifecycleExtensions;
import net.csdn.bootstrap.loader.impl.DocumentLoader;
import net.csdn.bootstrap.loader.impl.ModelLoader;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementDiagnostics;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.logging.log4j.LogConfigurator;
import net.csdn.bootstrap.WebEnhancementMetadata;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class ApplicationLifecycleTest {
    private static final String GOOD = "net.csdn.bootstrap.lifecycle.web.good";
    private static final String ANCHOR = GOOD + ".ServiceFrameworkPackageAnchor";
    private static final String TRACE = GOOD + ".ProbeTrace";
    private final List<ApplicationContext> contexts = new ArrayList<ApplicationContext>();
    private final List<URLClassLoader> loaders = new ArrayList<URLClassLoader>();

    @After
    public void tearDown() throws Exception {
        for (int i = contexts.size() - 1; i >= 0; i--) {
            try {
                contexts.get(i).close();
            } catch (Throwable ignored) {
                // The test already asserted the primary failure.
            }
        }
        for (int i = 0; i < loaders.size(); i++) {
            loaders.get(i).close();
        }
        contexts.clear();
        loaders.clear();
        LifecycleExtensions.reset();
    }

    @Test
    public void bootstrapServesFiltersInjectionAndClosesThePort() throws Exception {
        URLClassLoader loader = isolated();
        ApplicationContext context = startGood(loader, "alpha", 0, LifecycleExtensions.Recording.class.getName());
        ApplicationContext again = Bootstrap.configureSystem(context.settings(), marker(loader));
        Assert.assertSame(context, again);
        Assert.assertTrue(context.isHttpStarted());
        int port = context.httpPort();
        Assert.assertTrue(port > 0);

        HttpResult ok = http(port, "/probe");
        Assert.assertEquals(200, ok.status);
        Assert.assertEquals("svc:util:alpha", ok.body);
        Assert.assertEquals(Arrays.asList(
                "before", "outer-in", "inner-in", "action", "inner-out", "outer-out", "after"), events(context, loader));

        clear(context, loader);
        HttpResult boom = http(port, "/probe/explode");
        Assert.assertEquals(500, boom.status);
        Assert.assertTrue(boom.body.contains("boom"));
        Assert.assertEquals(Arrays.asList("before", "outer-in", "inner-in", "action", "after"), events(context, loader));

        clear(context, loader);
        HttpResult denied = http(port, "/probe/deny");
        Assert.assertEquals(500, denied.status);
        Assert.assertTrue(denied.body.contains("before-failed"));
        Assert.assertEquals(Arrays.asList("before-fail", "after"), events(context, loader));
        Assert.assertTrue(LifecycleExtensions.EVENTS.contains("recording-start"));

        context.close();
        Assert.assertFalse(context.isHttpStarted());
        assertRefused(port);
        context.close();
        Assert.assertTrue(LifecycleExtensions.EVENTS.contains("recording-close"));
        try {
            Bootstrap.configureSystem(baseSettings("alpha", 0, null).build(), marker(loader));
            Assert.fail("same loader was hot-reloaded");
        } catch (EnhancementFailure failure) {
            Assert.assertTrue(failure.getMessage().contains("hot reload"));
        }
        assertNoLeftoverWorkers();
    }

    @Test
    public void requiredEnhancementFailureDoesNotBindAndClosesResources() throws Exception {
        int port = freePort();
        URLClassLoader loader = isolated();
        try {
            startGood(loader, "fail", port, LifecycleExtensions.FailingRule.class.getName());
            Assert.fail("required enhancement failure started the application");
        } catch (EnhancementFailure failure) {
            Assert.assertTrue(failure.getMessage().contains("required enhancement failed"));
            Assert.assertEquals("enhance", failure.getPhase());
        }
        Assert.assertTrue(LifecycleExtensions.EVENTS.contains("failing-rule-close"));
        Assert.assertFalse(LifecycleExtensions.EVENTS.contains("failing-rule-start"));
        assertFree(port);
        assertNoLeftoverWorkers();
    }

    @Test
    public void badFilterIsRejectedBeforeThePortOpens() throws Exception {
        int port = freePort();
        URLClassLoader loader = isolated();
        Settings settings = httpSettings("token", port, null)
                .put("application.controller", "net.csdn.bootstrap.lifecycle.web.bad")
                .put("application.service", "")
                .put("application.util", "")
                .build();
        try {
            track(Bootstrap.configureSystem(settings, marker(loader, "net.csdn.bootstrap.lifecycle.web.bad.ServiceFrameworkPackageAnchor")));
            Assert.fail("missing filter method started the server");
        } catch (EnhancementFailure failure) {
            Assert.assertEquals("filter", failure.getPhase());
            Assert.assertTrue(failure.getMessage().contains("missingMethod"));
        }
        assertFree(port);
    }

    @Test
    public void extensionTopologyFailsBeforeStart() throws Exception {
        assertExtensionFailure(
                LifecycleExtensions.CycleLeft.class.getName() + "," + LifecycleExtensions.CycleRight.class.getName(),
                "cycle");
        Assert.assertFalse(LifecycleExtensions.EVENTS.contains("cycle-left-start"));
        Assert.assertFalse(LifecycleExtensions.EVENTS.contains("cycle-right-start"));
        LifecycleExtensions.reset();
        assertExtensionFailure(LifecycleExtensions.NeedsMissing.class.getName(), "unknown capability");
        Assert.assertFalse(LifecycleExtensions.EVENTS.contains("missing-start"));
        LifecycleExtensions.reset();
        assertExtensionFailure(
                LifecycleExtensions.DuplicateOne.class.getName() + "," + LifecycleExtensions.DuplicateTwo.class.getName(),
                "shared.capability");
    }

    @Test
    public void disabledMissingClassStillStarts() throws Exception {
        Settings settings = quietSettings()
                .put("application.extensions.disabled", "net.csdn.bootstrap.lifecycle.ext.DoesNotExist")
                .build();
        ApplicationContext context = track(Bootstrap.configureSystem(settings, ApplicationLifecycleTest.class));
        Assert.assertTrue(context.disabledExtensionClassNames().contains("net.csdn.bootstrap.lifecycle.ext.DoesNotExist"));
        Assert.assertTrue(context.disabledExtensionClassNames().contains("net.csdn.bootstrap.extension.OrmFrameworkExtension"));
        Assert.assertTrue(context.disabledExtensionClassNames().contains("net.csdn.bootstrap.extension.MongoFrameworkExtension"));
        Assert.assertFalse(context.isHttpStarted());
    }

    @Test
    public void startFailureClosesInReverseAndKeepsTheOriginal() throws Exception {
        Settings settings = quietSettings()
                .put("application.extensions",
                        LifecycleExtensions.StartFirst.class.getName() + "," + LifecycleExtensions.StartSecond.class.getName())
                .build();
        try {
            track(Bootstrap.configureSystem(settings, ApplicationLifecycleTest.class));
            Assert.fail("start failure was ignored");
        } catch (IllegalStateException e) {
            Assert.assertEquals("start-failed", e.getMessage());
            boolean sawClose = false;
            Throwable[] suppressed = e.getSuppressed();
            for (int i = 0; i < suppressed.length; i++) {
                if (containsMessage(suppressed[i], "close-first-failed")) {
                    sawClose = true;
                }
            }
            Assert.assertTrue(sawClose);
        }
        Assert.assertEquals(Arrays.asList("start-first", "start-second", "close-second", "close-first"),
                new ArrayList<String>(LifecycleExtensions.EVENTS));
    }

    @Test
    public void twoContextsDoNotShareRoutesOrThreads() throws Exception {
        URLClassLoader leftLoader = isolated();
        URLClassLoader rightLoader = isolated();
        ApplicationContext left = startGood(leftLoader, "left", 0, null);
        ApplicationContext right = startGood(rightLoader, "right", 0, null);
        Assert.assertNotSame(left.injector(), right.injector());
        HttpResult leftBody = http(left.httpPort(), "/probe");
        HttpResult rightBody = http(right.httpPort(), "/probe");
        Assert.assertEquals("svc:util:left", leftBody.body);
        Assert.assertEquals("svc:util:right", rightBody.body);
        int leftPort = left.httpPort();
        int rightPort = right.httpPort();
        left.close();
        assertRefused(leftPort);
        Assert.assertEquals("svc:util:right", http(rightPort, "/probe").body);
        right.close();
        assertRefused(rightPort);
        assertNoLeftoverWorkers();
    }

    @Test
    public void asyncWorkMustBindTheCapturedContext() throws Exception {
        Settings settings = quietSettings().build();
        final ApplicationContext context = track(Bootstrap.configureSystem(settings, ApplicationLifecycleTest.class));
        final ApplicationContext[] seen = new ApplicationContext[1];
        final Injector[] seenInjector = new Injector[1];
        Thread thread = new Thread(context.capture(new Runnable() {
            @Override
            public void run() {
                seen[0] = ApplicationContext.currentOrNull();
                seenInjector[0] = ServiceFramwork.currentInjector();
            }
        }));
        thread.start();
        thread.join(5000);
        Assert.assertFalse(thread.isAlive());
        Assert.assertSame(context, seen[0]);
        Assert.assertSame(context.injector(), seenInjector[0]);
        final ApplicationContext[] unbound = new ApplicationContext[1];
        Thread plain = new Thread(new Runnable() {
            @Override
            public void run() {
                unbound[0] = ApplicationContext.currentOrNull();
            }
        });
        plain.start();
        plain.join(5000);
        Assert.assertNull(unbound[0]);
    }

    @Test
    public void closeWhileAScopeIsActiveCanBeRetried() throws Exception {
        Settings settings = quietSettings().build();
        ApplicationContext context = track(Bootstrap.configureSystem(settings, ApplicationLifecycleTest.class));
        EnhancementContext.Scope scope = context.activate();
        try {
            context.close();
            Assert.fail("close finished while a scope was still active");
        } catch (EnhancementFailure failure) {
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("active scopes"));
        }
        Assert.assertFalse(context.isClosed());
        Assert.assertFalse(context.enhancementContext().isClosed());
        scope.close();
        context.close();
        Assert.assertTrue(context.isClosed());
        context.close();
    }

    @Test
    public void shutdownGateKeepsHttpUntilItWinsThenRejectsBothActivatePaths() throws Exception {
        URLClassLoader loader = isolated();
        final ApplicationContext context = startGood(loader, "gate", 0, null);
        final int port = context.httpPort();
        Assert.assertEquals(200, http(port, "/probe").status);
        final AtomicInteger closedResources = new AtomicInteger();
        context.enhancementContext().track(new Closeable() {
            @Override
            public void close() {
                closedResources.incrementAndGet();
                if (EnhancementContext.currentOrNull() != null) {
                    throw new IllegalStateException("resource closed while a scope was active");
                }
            }
        });

        final EnhancementContext.Scope scope = context.activate();
        final CyclicBarrier held = new CyclicBarrier(2);
        final AtomicReference<Throwable> closeFailure = new AtomicReference<Throwable>();
        final AtomicBoolean httpStillUp = new AtomicBoolean(false);
        Thread rejected = new Thread() {
            @Override
            public void run() {
                try {
                    held.await(5, TimeUnit.SECONDS);
                    try {
                        context.close();
                        closeFailure.set(new AssertionError("close finished while a scope was active"));
                    } catch (EnhancementFailure failure) {
                        closeFailure.set(failure);
                    }
                    httpStillUp.set(http(port, "/probe").status == 200);
                } catch (Throwable thrown) {
                    closeFailure.compareAndSet(null, thrown);
                }
            }
        };
        rejected.start();
        held.await(5, TimeUnit.SECONDS);
        rejected.join(8000);
        Assert.assertFalse(rejected.isAlive());
        Assert.assertTrue(closeFailure.get() instanceof EnhancementFailure);
        Assert.assertTrue(closeFailure.get().getMessage(), closeFailure.get().getMessage().contains("active scopes"));
        Assert.assertTrue(httpStillUp.get());
        Assert.assertTrue(context.isHttpStarted());
        Assert.assertFalse(context.isClosed());
        Assert.assertFalse(context.enhancementContext().isShutdownAdmitted());
        Assert.assertEquals(0, closedResources.get());
        scope.close();

        final EnhancementContext direct = context.enhancementContext();
        final CyclicBarrier race = new CyclicBarrier(2);
        final AtomicInteger activated = new AtomicInteger();
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread activator = new Thread() {
            @Override
            public void run() {
                try {
                    race.await(5, TimeUnit.SECONDS);
                    long deadline = System.currentTimeMillis() + 5000;
                    while (!direct.isShutdownAdmitted() && System.currentTimeMillis() < deadline) {
                        Thread.yield();
                    }
                    if (!direct.isShutdownAdmitted()) {
                        error.compareAndSet(null, new AssertionError("shutdown was not admitted"));
                        return;
                    }
                    try {
                        context.activate();
                        activated.incrementAndGet();
                    } catch (RuntimeException ignored) {
                        // The application gate and the context gate both have to fail.
                    }
                    try {
                        direct.activate();
                        activated.incrementAndGet();
                    } catch (RuntimeException ignored) {
                        // Direct activation uses the same reservation.
                    }
                } catch (Throwable thrown) {
                    error.compareAndSet(null, thrown);
                }
            }
        };
        Thread closer = new Thread() {
            @Override
            public void run() {
                try {
                    race.await(5, TimeUnit.SECONDS);
                    context.close();
                } catch (Throwable thrown) {
                    error.compareAndSet(null, thrown);
                }
            }
        };
        activator.start();
        closer.start();
        activator.join(8000);
        closer.join(8000);
        Assert.assertFalse(activator.isAlive());
        Assert.assertFalse(closer.isAlive());
        Assert.assertNull(error.get() == null ? null : error.get().toString(), error.get());
        Assert.assertEquals(0, activated.get());
        Assert.assertTrue(direct.isClosed());
        Assert.assertTrue(context.isClosed());
        Assert.assertEquals(1, closedResources.get());
        Assert.assertFalse(context.isHttpStarted());
        assertRefused(port);
        context.close();
        Assert.assertEquals(1, closedResources.get());
    }

    @Test
    public void badLoggingFailsBeforeThePortAndAValidFileCanStart() throws Exception {
        LogConfigurator.reset();
        int port = freePort();
        File root = new File("target/logging-startup-" + System.nanoTime());
        File badConf = new File(root, "bad");
        Assert.assertTrue(badConf.mkdirs());
        write(new File(badConf, "logging.yml"),
                "rootLogger: INFO,console\n"
                        + "appender:\n"
                        + "  console:\n"
                        + "    type: notARealAppender\n"
                        + "    layout:\n"
                        + "      type: pattern\n"
                        + "      conversionPattern: \"%m%n\"\n");
        Settings bad = quietSettings()
                .put("path.conf", badConf.getAbsolutePath())
                .put("datasources.password", "super-secret-db-value")
                .put("http.disable", "false")
                .put("http.port", String.valueOf(port))
                .put("http.host", "127.0.0.1")
                .build();
        try {
            track(Bootstrap.configureSystem(bad, ApplicationLifecycleTest.class));
            Assert.fail("bad logging configuration opened the application");
        } catch (RuntimeException thrown) {
            Assert.assertFalse(String.valueOf(thrown), String.valueOf(thrown).contains("super-secret-db-value"));
        }
        assertFree(port);
        URLClassLoader loader = isolated();
        ApplicationContext started = startGood(loader, "logs-ok", 0, null);
        Assert.assertEquals("svc:util:logs-ok", http(started.httpPort(), "/probe").body);
        started.close();
        assertRefused(started.httpPort());
    }

    @Test
    public void controllerDiagnosticsStayOffByDefaultAndRecordFilterOriginWhenEnabled() throws Exception {
        URLClassLoader quietLoader = isolated();
        ApplicationContext quiet = startGood(quietLoader, "diag-off", 0, null);
        Assert.assertEquals("svc:util:diag-off", http(quiet.httpPort(), "/probe").body);
        EnhancementDiagnostics off = quiet.enhancementContext().diagnostics();
        Assert.assertFalse(off.enabled());
        Assert.assertEquals(0, off.hashComputations());
        Assert.assertEquals(0, off.bytecodeReads());
        Assert.assertEquals(0, off.methodInspections());
        Assert.assertEquals(0, off.diskWrites());
        Assert.assertFalse(off.emitGeneratedSource());
        Assert.assertFalse(off.emitClassFiles());

        File reportDir = new File("target/controller-diagnostics/normal");
        deleteTree(reportDir);
        Assert.assertTrue(reportDir.mkdirs());
        EnhancementDiagnostics diagnostics = EnhancementDiagnostics.enabled(reportDir);
        String previousDigest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        diagnostics.noteSafeMetadata("app-rev-1", previousDigest);
        URLClassLoader loudLoader = isolated();
        Settings settings = httpSettings("diag-on", 0, null)
                .put("datasources.password", "super-secret-db-value")
                .build();
        ApplicationContext loud = track(Bootstrap.configureSystem(settings, marker(loudLoader), diagnostics));
        Assert.assertEquals("svc:util:diag-on", http(loud.httpPort(), "/probe").body);
        Assert.assertEquals(http(quiet.httpPort(), "/probe").body.replace("diag-off", "diag-on"),
                http(loud.httpPort(), "/probe").body);
        String className = GOOD + ".ProbeController";
        String added = "beforeFilter(Ljava/lang/String;Ljava/util/Map;)V";
        EnhancementDiagnostics.MethodOrigin addedOrigin = diagnostics.originOf(className, added);
        Assert.assertNotNull(methodDump(diagnostics, className), addedOrigin);
        Assert.assertEquals("controller-filter", addedOrigin.ruleId());
        Assert.assertEquals(1, addedOrigin.ruleVersion());
        Assert.assertEquals(EnhancementDiagnostics.MethodChange.ADDED, addedOrigin.change());
        String beforeHash = diagnostics.originalHash(className);
        Assert.assertNotNull(beforeHash);
        Assert.assertEquals(64, beforeHash.length());
        EnhancementDiagnostics.Event apply = findApply(diagnostics, className);
        Assert.assertEquals(beforeHash, apply.originalSha256());
        Assert.assertEquals("app-rev-1", apply.configVersion());
        Assert.assertEquals(WebEnhancementMetadata.controllerDigest(Collections.singletonList(className)), apply.schemaDigest());
        Assert.assertEquals("app-rev-1", diagnostics.configVersion());
        Assert.assertEquals(previousDigest, diagnostics.schemaDigest());
        Assert.assertFalse(diagnostics.emitGeneratedSource());
        Assert.assertFalse(diagnostics.emitClassFiles());
        loud.close();
        quiet.close();
        String report = read(new File(reportDir, "report.txt"));
        Assert.assertTrue(report, report.contains("rule=controller-filter"));
        Assert.assertTrue(report, report.contains("beforeFilter(Ljava/lang/String;Ljava/util/Map;)V"));
        Assert.assertTrue(report, report.contains("configVersion=app-rev-1"));
        Assert.assertFalse(report, report.contains("configVersion=" + WebEnhancementMetadata.VERSION));
        Assert.assertTrue(report, report.contains(beforeHash));
        Assert.assertFalse(report, report.contains("super-secret-db-value"));
        Assert.assertFalse(report, report.contains("datasources.password"));
        Assert.assertFalse(new File(reportDir, "sources").exists());
        Assert.assertFalse(new File(reportDir, "original").exists());
        Assert.assertFalse(new File(reportDir, "enhanced").exists());
    }

    @Test
    public void filterAndRequiredEnhancementFailuresAreReportedBeforeListen() throws Exception {
        File filterDir = new File("target/controller-diagnostics/filter-failure");
        deleteTree(filterDir);
        Assert.assertTrue(filterDir.mkdirs());
        EnhancementDiagnostics filterDiagnostics = EnhancementDiagnostics.enabled(filterDir);
        int filterPort = freePort();
        URLClassLoader filterLoader = isolated();
        Settings filterSettings = httpSettings("bad-filter", filterPort, null)
                .put("application.controller", "net.csdn.bootstrap.lifecycle.web.bad")
                .put("application.service", "")
                .put("application.util", "")
                .put("datasources.password", "super-secret-db-value")
                .build();
        try {
            track(Bootstrap.configureSystem(
                    filterSettings,
                    marker(filterLoader, "net.csdn.bootstrap.lifecycle.web.bad.ServiceFrameworkPackageAnchor"),
                    filterDiagnostics));
            Assert.fail("missing filter method started the server");
        } catch (EnhancementFailure failure) {
            Assert.assertEquals("filter", failure.getPhase());
            Assert.assertTrue(failure.getClassName(), failure.getClassName().contains("BadFilterController"));
            Assert.assertEquals("controller-filter", failure.getRuleId());
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("missingMethod"));
            Assert.assertFalse(failure.getMessage(), failure.getMessage().contains("super-secret-db-value"));
        }
        assertFree(filterPort);
        EnhancementDiagnostics.Event filterEvent = findPhase(filterDiagnostics, "filter");
        Assert.assertTrue(filterEvent.className(), filterEvent.className().contains("BadFilterController"));
        Assert.assertEquals("controller-filter", filterEvent.ruleId());
        Assert.assertEquals("filter", filterEvent.phase());
        Assert.assertEquals(WebEnhancementMetadata.APPLICATION_REVISION, filterEvent.configVersion());
        String filterReport = read(new File(filterDir, "report.txt"));
        Assert.assertTrue(filterReport, filterReport.contains("phase=filter"));
        Assert.assertTrue(filterReport, filterReport.contains("rule=controller-filter"));
        Assert.assertTrue(filterReport, filterReport.contains("BadFilterController"));
        Assert.assertFalse(filterReport, filterReport.contains("super-secret-db-value"));

        File enhanceDir = new File("target/controller-diagnostics/enhance-failure");
        deleteTree(enhanceDir);
        Assert.assertTrue(enhanceDir.mkdirs());
        EnhancementDiagnostics enhanceDiagnostics = EnhancementDiagnostics.enabled(enhanceDir);
        int enhancePort = freePort();
        URLClassLoader enhanceLoader = isolated();
        try {
            Settings enhanceSettings = httpSettings("enhance-fail", enhancePort, LifecycleExtensions.FailingRule.class.getName())
                    .put("datasources.password", "super-secret-db-value")
                    .build();
            track(Bootstrap.configureSystem(enhanceSettings, marker(enhanceLoader), enhanceDiagnostics));
            Assert.fail("required enhancement failure started the application");
        } catch (EnhancementFailure failure) {
            Assert.assertEquals("enhance", failure.getPhase());
            Assert.assertTrue(failure.getClassName(), failure.getClassName().contains("ProbeController"));
            Assert.assertEquals("fail-controller", failure.getRuleId());
            Assert.assertFalse(failure.getMessage(), failure.getMessage().contains("super-secret-db-value"));
        }
        assertFree(enhancePort);
        EnhancementDiagnostics.Event enhanceEvent = findFailure(enhanceDiagnostics, "fail-controller");
        Assert.assertEquals("apply", enhanceEvent.phase());
        Assert.assertTrue(enhanceEvent.className(), enhanceEvent.className().contains("ProbeController"));
        Assert.assertEquals("fail-controller", enhanceEvent.ruleId());
        Assert.assertTrue(enhanceEvent.failure(), enhanceEvent.failure().contains("phase=enhance"));
        Assert.assertEquals(WebEnhancementMetadata.APPLICATION_REVISION, enhanceEvent.configVersion());
        String enhanceReport = read(new File(enhanceDir, "report.txt"));
        Assert.assertTrue(enhanceReport, enhanceReport.contains("rule=fail-controller"));
        Assert.assertTrue(enhanceReport, enhanceReport.contains("ProbeController"));
        Assert.assertFalse(enhanceReport, enhanceReport.contains("super-secret-db-value"));
        Assert.assertFalse(new File(enhanceDir, "sources").exists());
        Assert.assertFalse(new File(filterDir, "sources").exists());
    }

    @Test
    public void failedStartLeavesTheLoaderReusable() throws Exception {
        URLClassLoader loader = isolated();
        int port = freePort();
        try {
            startGood(loader, "fail", port, LifecycleExtensions.FailingRule.class.getName());
            Assert.fail("required enhancement failure started the application");
        } catch (EnhancementFailure failure) {
            Assert.assertTrue(failure.getMessage().contains("required enhancement failed"));
        }
        assertFree(port);
        ApplicationContext again = startGood(loader, "after-fail", 0, null);
        Assert.assertEquals("svc:util:after-fail", http(again.httpPort(), "/probe").body);
        again.close();
        assertRefused(again.httpPort());
    }

    @Test
    public void equalLoadersDoNotBlockEachOther() throws Exception {
        URL url = ApplicationLifecycleTest.class.getProtectionDomain().getCodeSource().getLocation();
        EqualLoader first = new EqualLoader(new java.net.URL[]{url});
        EqualLoader second = new EqualLoader(new java.net.URL[]{url});
        loaders.add(first);
        loaders.add(second);
        Assert.assertEquals(first, second);
        Assert.assertFalse(first == second);
        ApplicationContext started = startGood(first, "equal-a", 0, null);
        started.close();
        ApplicationContext other = startGood(second, "equal-b", 0, null);
        Assert.assertEquals("svc:util:equal-b", http(other.httpPort(), "/probe").body);
        other.close();
        try {
            startGood(first, "equal-again", 0, null);
            Assert.fail("the same live loader was defined twice");
        } catch (EnhancementFailure failure) {
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("hot reload"));
        }
    }

    @Test
    public void definedLoaderCanBeCollectedAfterClose() throws Exception {
        WeakReference<ClassLoader> ref = startCloseAndDrop();
        Object leftover = new byte[1024];
        for (int i = 0; i < 8 && ref.get() != null; i++) {
            leftover = new byte[1024 * 1024];
            System.gc();
            System.runFinalization();
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        Assert.assertNull(leftover == null ? "unused" : ref.get());
    }

    @Test
    public void disabledExtensionsAreNotLoadedFromARefusingLoader() throws Exception {
        URL url = ApplicationLifecycleTest.class.getProtectionDomain().getCodeSource().getLocation();
        final List<String> refused = new ArrayList<String>();
        RefusingLoader loader = new RefusingLoader(new java.net.URL[]{url}, refused);
        loaders.add(loader);
        Class<?> marker = marker(loader);
        Settings settings = quietSettings().build();
        ApplicationContext context = track(Bootstrap.configureSystem(settings, marker));
        Assert.assertTrue(context.disabledExtensionClassNames().contains(BuiltinExtensions.ORM));
        Assert.assertTrue(context.disabledExtensionClassNames().contains(BuiltinExtensions.MONGO));
        Assert.assertTrue(refused.toString(), refused.isEmpty());
        Assert.assertFalse(context.isHttpStarted());
    }

    @Test
    public void legacyLoadersPropagateFailures() throws Exception {
        try {
            new ModelLoader().load(quietSettings().build());
            Assert.fail("ModelLoader ran without an application");
        } catch (EnhancementFailure failure) {
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("no application context"));
        }
        try {
            new DocumentLoader().load(quietSettings().build());
            Assert.fail("DocumentLoader ran without an application");
        } catch (EnhancementFailure failure) {
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("no application context"));
        }

        ApplicationContext context = track(ApplicationContext.open(ApplicationLifecycleTest.class));
        EnhancementContext.Scope scope = context.activate();
        try {
            try {
                new DocumentLoader().load(quietSettings()
                        .put("development.datasources.mongodb.disable", "false")
                        .put("application.document", " ")
                        .build());
                Assert.fail("DocumentLoader swallowed a missing package");
            } catch (EnhancementFailure failure) {
                Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("application.document"));
            }
            try {
                new ModelLoader().load(quietSettings()
                        .put("development.datasources.mysql.disable", "false")
                        .put("development.datasources.mysql.host", "127.0.0.1")
                        .put("development.datasources.mysql.port", "1")
                        .put("development.datasources.mysql.database", "sf_compat")
                        .put("development.datasources.mysql.username", "unused")
                        .put("development.datasources.mysql.password", "unused")
                        .put("development.datasources.mysql.driver", "com.mysql.jdbc.Driver")
                        .put("development.datasources.mysql.jdbc.connectTimeout", "1000")
                        .put("application.model", "net.csdn.bootstrap.lifecycle.web.db.orm")
                        .build());
                Assert.fail("ModelLoader swallowed a connection failure");
            } catch (EnhancementFailure failure) {
                Assert.assertTrue(failure.getMessage(), failure.getMessage().length() > 0);
            }
        } finally {
            scope.close();
        }
        Assert.assertFalse(context.isClosed());
        context.close();
        Assert.assertTrue(context.isClosed());
    }

    private static WeakReference<ClassLoader> startCloseAndDrop() throws Exception {
        URL url = ApplicationLifecycleTest.class.getProtectionDomain().getCodeSource().getLocation();
        IsolatedLoader loader = new IsolatedLoader(new java.net.URL[]{url});
        Class<?> marker = marker(loader);
        ApplicationContext context = ApplicationContext.open(marker);
        context.markClassesDefined();
        try {
            ApplicationContext.open(marker);
            Assert.fail("the same live loader was defined twice");
        } catch (EnhancementFailure failure) {
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("hot reload"));
        }
        context.close();
        Assert.assertTrue(context.isClosed());
        WeakReference<ClassLoader> ref = new WeakReference<ClassLoader>(loader);
        loader.close();
        return ref;
    }

    private ApplicationContext startGood(URLClassLoader loader, String token, int port, String extension) throws Exception {
        Settings settings = httpSettings(token, port, extension).build();
        return track(Bootstrap.configureSystem(settings, marker(loader)));
    }

    private static String methodDump(EnhancementDiagnostics diagnostics, String className) {
        StringBuilder builder = new StringBuilder();
        List<EnhancementDiagnostics.Event> events = diagnostics.eventsFor(className);
        for (int i = 0; i < events.size(); i++) {
            List<EnhancementDiagnostics.MethodChange> changes = events.get(i).methodChanges();
            for (int j = 0; j < changes.size(); j++) {
                builder.append(changes.get(j).change()).append(' ').append(changes.get(j).signature()).append('\n');
            }
        }
        return builder.toString();
    }

    private static EnhancementDiagnostics.Event findApply(EnhancementDiagnostics diagnostics, String className) {
        List<EnhancementDiagnostics.Event> events = diagnostics.eventsFor(className);
        for (int i = 0; i < events.size(); i++) {
            EnhancementDiagnostics.Event event = events.get(i);
            if ("apply".equals(event.phase()) && "controller-filter".equals(event.ruleId())) {
                return event;
            }
        }
        Assert.fail(methodDump(diagnostics, className));
        return null;
    }

    private static EnhancementDiagnostics.Event findPhase(EnhancementDiagnostics diagnostics, String phase) {
        List<EnhancementDiagnostics.Event> events = diagnostics.events();
        for (int i = 0; i < events.size(); i++) {
            if (phase.equals(events.get(i).phase())) {
                return events.get(i);
            }
        }
        Assert.fail(phase);
        return null;
    }

    private static EnhancementDiagnostics.Event findFailure(EnhancementDiagnostics diagnostics, String ruleId) {
        List<EnhancementDiagnostics.Event> events = diagnostics.events();
        for (int i = 0; i < events.size(); i++) {
            EnhancementDiagnostics.Event event = events.get(i);
            if (ruleId.equals(event.ruleId()) && event.failure() != null && event.failure().length() > 0) {
                return event;
            }
        }
        Assert.fail(ruleId);
        return null;
    }

    private static void write(File dest, String text) throws Exception {
        File parent = dest.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        FileOutputStream output = new FileOutputStream(dest);
        OutputStreamWriter writer = new OutputStreamWriter(output, "UTF-8");
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }

    private static String read(File file) throws Exception {
        java.io.FileInputStream input = new java.io.FileInputStream(file);
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

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteTree(children[i]);
                }
            }
        }
        file.delete();
    }

    private void assertExtensionFailure(String extensions, String messagePart) throws Exception {
        Settings settings = quietSettings().put("application.extensions", extensions).build();
        try {
            track(Bootstrap.configureSystem(settings, ApplicationLifecycleTest.class));
            Assert.fail("extension topology started");
        } catch (EnhancementFailure failure) {
            Assert.assertEquals("extension", failure.getPhase());
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains(messagePart));
        }
    }

    private ApplicationContext track(ApplicationContext context) {
        contexts.add(context);
        return context;
    }

    private URLClassLoader isolated() throws Exception {
        URL url = ApplicationLifecycleTest.class.getProtectionDomain().getCodeSource().getLocation();
        IsolatedLoader loader = new IsolatedLoader(new java.net.URL[]{url});
        loaders.add(loader);
        return loader;
    }

    private static Class<?> marker(URLClassLoader loader) throws Exception {
        return marker(loader, ANCHOR);
    }

    private static Class<?> marker(URLClassLoader loader, String name) throws Exception {
        return Class.forName(name, false, loader);
    }

    private static ImmutableSettings.Builder httpSettings(String token, int port, String extension) {
        ImmutableSettings.Builder builder = baseSettings(token, port, extension)
                .put("http.disable", "false")
                .put("http.port", String.valueOf(port))
                .put("http.host", "127.0.0.1")
                .put("http.threads.min", "8")
                .put("http.threads.max", "64")
                .put("application.controller", GOOD)
                .put("application.service", GOOD)
                .put("application.util", GOOD);
        return builder;
    }

    private static ImmutableSettings.Builder baseSettings(String token, int port, String extension) {
        ImmutableSettings.Builder builder = quietSettings()
                .put("application.token", token)
                .put("http.port", String.valueOf(port));
        if (extension != null) {
            builder.put("application.extensions", extension);
        }
        return builder;
    }

    private static ImmutableSettings.Builder quietSettings() {
        return ImmutableSettings.settingsBuilder()
                .put("mode", "test")
                .put("path.conf", configDir().getAbsolutePath())
                .put("path.logs", new File("target/logs").getAbsolutePath())
                .put("cluster.name", "sf-web-lifecycle")
                .put("test.datasources.mysql.disable", "true")
                .put("test.datasources.mongodb.disable", "true")
                .put("test.datasources.redis.disable", "true")
                .put("http.disable", "true")
                .put("thrift.disable", "true")
                .put("dubbo.disable", "true")
                .put("application.template.engine.enable", "false")
                .put("application.api.qps.enable", "false")
                .put("application.log.enable", "false")
                .put("application.controller", "")
                .put("application.controller.default", "")
                .put("application.controllerNames", "")
                .put("application.service", "")
                .put("application.util", "")
                .put("qpslimit.enable", "false");
    }

    private static File configDir() {
        File direct = new File("config");
        if (new File(direct, "logging.yml").isFile()) {
            return direct;
        }
        File parent = new File("../config");
        if (new File(parent, "logging.yml").isFile()) {
            return parent;
        }
        throw new IllegalStateException("logging.yml was not found from " + new File("").getAbsolutePath());
    }

    @SuppressWarnings("unchecked")
    private static List<String> events(ApplicationContext context, ClassLoader loader) throws Exception {
        Class<?> type = Class.forName(TRACE, false, loader);
        Object trace = context.injector().getInstance(type);
        return (List<String>) type.getMethod("events").invoke(trace);
    }

    private static void clear(ApplicationContext context, ClassLoader loader) throws Exception {
        Class<?> type = Class.forName(TRACE, false, loader);
        Object trace = context.injector().getInstance(type);
        type.getMethod("clear").invoke(trace);
    }

    private static HttpResult http(int port, String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setRequestMethod("GET");
        int status;
        String body;
        try {
            status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            body = read(stream);
        } finally {
            connection.disconnect();
        }
        return new HttpResult(status, body);
    }

    private static String read(InputStream stream) {
        if (stream == null) {
            return "";
        }
        Scanner scanner = new Scanner(stream, "UTF-8").useDelimiter("\\A");
        try {
            return scanner.hasNext() ? scanner.next() : "";
        } finally {
            scanner.close();
        }
    }

    private static void assertRefused(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 4000;
        IOException last = null;
        while (System.currentTimeMillis() < deadline) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
                last = new IOException("still accepting " + port);
            } catch (IOException expected) {
                return;
            } finally {
                socket.close();
            }
            Thread.sleep(100);
        }
        throw last == null ? new IOException("port " + port + " was not checked") : last;
    }

    private static int freePort() throws Exception {
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", 0));
            return socket.getLocalPort();
        } finally {
            socket.close();
        }
    }

    private static void assertFree(int port) throws Exception {
        ServerSocket socket = new ServerSocket();
        try {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress("127.0.0.1", port));
        } finally {
            socket.close();
        }
    }

    private static void assertNoLeftoverWorkers() throws Exception {
        long deadline = System.currentTimeMillis() + 4000;
        String leftover = workerThreads();
        while (leftover != null && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
            leftover = workerThreads();
        }
        Assert.assertNull(leftover);
    }

    private static String workerThreads() {
        Thread[] threads = new Thread[Thread.activeCount() + 32];
        int count = Thread.enumerate(threads);
        for (int i = 0; i < count; i++) {
            Thread thread = threads[i];
            if (thread == null || !thread.isAlive()) {
                continue;
            }
            String name = thread.getName();
            if (name == null) {
                continue;
            }
            if (name.startsWith("qtp") || name.contains("Connection evictor") || name.contains("Jetty")) {
                return name;
            }
        }
        return null;
    }

    private static boolean containsMessage(Throwable thrown, String part) {
        if (thrown == null) {
            return false;
        }
        if (thrown.getMessage() != null && thrown.getMessage().contains(part)) {
            return true;
        }
        Throwable[] suppressed = thrown.getSuppressed();
        for (int i = 0; i < suppressed.length; i++) {
            if (containsMessage(suppressed[i], part)) {
                return true;
            }
        }
        return containsMessage(thrown.getCause(), part);
    }

    private static final class EqualLoader extends URLClassLoader {
        private EqualLoader(java.net.URL[] urls) {
            super(urls, ApplicationLifecycleTest.class.getClassLoader());
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualLoader;
        }

        @Override
        public int hashCode() {
            return 42;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.csdn.bootstrap.lifecycle.web.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class RefusingLoader extends URLClassLoader {
        private final List<String> refused;

        private RefusingLoader(java.net.URL[] urls, List<String> refused) {
            super(urls, ApplicationLifecycleTest.class.getClassLoader());
            this.refused = refused;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (isHidden(name)) {
                refused.add(name);
                throw new ClassNotFoundException("refused " + name);
            }
            if (name.startsWith("net.csdn.bootstrap.lifecycle.web.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }

        private static boolean isHidden(String name) {
            return BuiltinExtensions.ORM.equals(name)
                    || BuiltinExtensions.MONGO.equals(name)
                    || name.startsWith("net.csdn.jpa.")
                    || name.startsWith("net.csdn.mongo.");
        }
    }

    private static final class IsolatedLoader extends URLClassLoader {
        private IsolatedLoader(URL[] urls) {
            super(urls, ApplicationLifecycleTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.csdn.bootstrap.lifecycle.web.")) {
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    private static final class HttpResult {
        private final int status;
        private final String body;

        private HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
