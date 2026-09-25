package net.csdn.junit;

import com.google.inject.Guice;
import com.google.inject.Injector;
import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.Bootstrap;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.exception.FailedToResolveConfigException;
import net.csdn.common.settings.Settings;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Legacy {@link IocTest} and {@code runner.DynamicSuite} must throw required
 * startup failures instead of printing them. DynamicSuite is outside the Maven
 * source roots, so this test compiles that source file and loads it itself.
 */
public class LegacyEntryStartupFailureTest {
    private static final String BAD_CONTROLLER = "net.csdn.bootstrap.lifecycle.web.bad.ServiceFrameworkPackageAnchor";
    private static final String NEEDS_MISSING = "net.csdn.bootstrap.lifecycle.ext.LifecycleExtensions$NeedsMissing";
    private static File compiledSuite;

    private Globals globals;
    private final List<URLClassLoader> loaders = new ArrayList<URLClassLoader>();

    @BeforeClass
    public static void compileRealDynamicSuite() throws Exception {
        File source = dynamicSuiteSource();
        compiledSuite = new File(tempRoot("dynsuite-classes"), "classes");
        Assert.assertTrue(compiledSuite.mkdirs());
        compile(source, compiledSuite);
    }

    @Before
    public void captureGlobals() throws Exception {
        globals = Globals.capture();
    }

    @After
    public void restoreGlobals() throws Exception {
        for (int i = 0; i < loaders.size(); i++) {
            try {
                loaders.get(i).close();
            } catch (Throwable ignored) {
                // The test already asserted the primary failure.
            }
        }
        loaders.clear();
        try {
            Bootstrap.shutdown();
        } catch (Throwable ignored) {
            // Restore still has to put the process flags back.
        }
        globals.restore();
    }

    @Test
    public void missingConfigurationIsThrownFromIocTest() throws Exception {
        File missing = new File(tempRoot("missing-config"), "application.yml");
        ServiceFramwork.applicaionYamlName(missing.getAbsolutePath());
        Injector stale = Guice.createInjector();
        IocTest.injector = stale;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        System.setErr(new PrintStream(err, true, "UTF-8"));
        try {
            IocTest.initEnv(LegacyEntryStartupFailureTest.class);
            Assert.fail("missing configuration was printed and returned");
        } catch (FailedToResolveConfigException failure) {
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains(missing.getName()));
            Assert.assertTrue(stackContainsInitEnv(failure));
        } finally {
            System.setErr(previous);
        }
        Assert.assertFalse(err.toString("UTF-8"), err.toString("UTF-8").contains("at net.csdn.junit.IocTest.initEnv"));
        Assert.assertNull(IocTest.injector);
    }

    @Test
    public void requiredFilterFailureIsThrownFromIocTestAndClearsInjector() throws Exception {
        File root = tempRoot("filter-failure");
        File config = writeQuietConfig(root, "net.csdn.bootstrap.lifecycle.web.bad", null);
        ServiceFramwork.applicaionYamlName(config.getAbsolutePath());
        URLClassLoader loader = childWebLoader();
        Class<?> marker = Class.forName(BAD_CONTROLLER, false, loader);
        Injector stale = Guice.createInjector();
        IocTest.injector = stale;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        System.setErr(new PrintStream(err, true, "UTF-8"));
        try {
            IocTest.initEnv(marker);
            Assert.fail("required filter failure was printed and returned");
        } catch (EnhancementFailure failure) {
            Assert.assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
            Assert.assertEquals("filter", failure.getPhase());
            Assert.assertEquals("controller-filter", failure.getRuleId());
            Assert.assertTrue(failure.getClassName(), failure.getClassName().contains("BadFilterController"));
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("missingMethod"));
            Assert.assertTrue(stackContainsInitEnv(failure));
        } finally {
            System.setErr(previous);
        }
        Assert.assertFalse(err.toString("UTF-8"), err.toString("UTF-8").contains("at net.csdn.junit.IocTest.initEnv"));
        Assert.assertNull(IocTest.injector);
        Assert.assertNotSame(stale, IocTest.injector);
    }

    @Test
    public void iocTestInitEnvKeepsInjectorAfterSuccessfulBoot() throws Exception {
        File root = tempRoot("ioc-success");
        File config = writeQuietConfig(root, "", null);
        ServiceFramwork.applicaionYamlName(config.getAbsolutePath());
        URLClassLoader loader = childWebLoader();
        Class<?> marker = Class.forName(
                "net.csdn.bootstrap.lifecycle.web.good.ServiceFrameworkPackageAnchor", false, loader);
        IocTest.initEnv(marker);
        Assert.assertNotNull(IocTest.injector);
        Assert.assertSame(ServiceFramwork.injector, IocTest.injector);
        Settings settings = IocTest.injector.getInstance(Settings.class);
        Assert.assertEquals("sf-legacy-entry", settings.get("cluster.name"));
        Assert.assertEquals("kept", settings.get("application.token"));
    }

    @Test
    public void dynamicSuiteStaticInitAbortsOnDependencyFailure() throws Exception {
        File root = tempRoot("suite-dependency");
        File config = writeQuietConfig(root, "", NEEDS_MISSING);
        ServiceFramwork.applicaionYamlName(config.getAbsolutePath());
        URLClassLoader loader = suiteLoader(compiledSuite.toURI().toURL());
        Class<?> suite = Class.forName("runner.DynamicSuite", false, loader);
        Assert.assertTrue(String.valueOf(suite.getProtectionDomain().getCodeSource()),
                suite.getProtectionDomain().getCodeSource().getLocation().toString().contains("dynsuite-classes"));
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        System.setErr(new PrintStream(err, true, "UTF-8"));
        try {
            Class.forName("runner.DynamicSuite", true, loader);
            Assert.fail("DynamicSuite printed the startup failure and finished initializing");
        } catch (ExceptionInInitializerError failure) {
            Throwable cause = failure.getCause();
            Assert.assertTrue(String.valueOf(cause), cause instanceof EnhancementFailure);
            EnhancementFailure enhancement = (EnhancementFailure) cause;
            Assert.assertEquals(EnhancementFailure.Category.DEPENDENCY, enhancement.getCategory());
            Assert.assertEquals("extension", enhancement.getPhase());
            Assert.assertEquals(NEEDS_MISSING, enhancement.getClassName());
            Assert.assertTrue(enhancement.getMessage(), enhancement.getMessage().contains("unknown capability"));
            Assert.assertNull(enhancement.getRuleId());
        } finally {
            System.setErr(previous);
        }
        Assert.assertFalse(err.toString("UTF-8"), err.toString("UTF-8").contains("at runner.DynamicSuite.initEnv"));
    }

    @Test
    public void dynamicSuiteClosesUnvisitedStreamsWhenClassLookupFails() throws Exception {
        File root = tempRoot("suite-close");
        File fixtures = new File(root, "fixtures");
        compileFixtures(fixtures);
        File config = writeQuietConfig(root, "", null);
        rewriteTestPackage(config, "dynfail");
        SpyLoader spy = track(new SpyLoader(new URL[]{fixtures.toURI().toURL()}));
        Class<?> marker = Class.forName("dynfail.First", false, spy);
        ServiceFramwork.scanService.setLoader(marker);
        ServiceFramwork.applicaionYamlName(config.getAbsolutePath());
        URLClassLoader suiteLoader = suiteLoader(compiledSuite.toURI().toURL());
        Class.forName("runner.DynamicSuite", true, suiteLoader);
        int openedBeforeLookup = spy.opened.size();
        Method find = Class.forName("runner.DynamicSuite", false, suiteLoader).getMethod("findTestClass");
        try {
            find.invoke(null);
            Assert.fail("missing test class was ignored");
        } catch (InvocationTargetException failure) {
            Assert.assertTrue(String.valueOf(failure.getCause()), failure.getCause() instanceof ClassNotFoundException);
            Assert.assertTrue(failure.getCause().getMessage(), failure.getCause().getMessage().contains("dynfail.First"));
        }
        assertClosedAfter(spy, openedBeforeLookup, "dynfail/Second.class");
        assertClosedAfter(spy, openedBeforeLookup, "dynfail/First.class");
    }

    @Test
    public void dynamicSuiteDiscoversTestClassesAndClosesStreams() throws Exception {
        File root = tempRoot("suite-discover");
        File fixtures = new File(root, "fixtures");
        compileFixtures(fixtures);
        File config = writeQuietConfig(root, "", null);
        rewriteTestPackage(config, "dynfail");
        SpyLoader spy = track(new SpyLoader(new URL[]{compiledSuite.toURI().toURL(), fixtures.toURI().toURL()}));
        Class<?> marker = Class.forName("dynfail.First", false, spy);
        ServiceFramwork.scanService.setLoader(marker);
        ServiceFramwork.applicaionYamlName(config.getAbsolutePath());
        Class<?> suite = Class.forName("runner.DynamicSuite", true, spy);
        int openedBeforeLookup = spy.opened.size();
        Method find = suite.getMethod("findTestClass");
        Class[] found = (Class[]) find.invoke(null);
        Assert.assertEquals(2, found.length);
        Assert.assertEquals("dynfail.First", found[0].getName());
        Assert.assertEquals("dynfail.Second", found[1].getName());
        Assert.assertSame(spy, found[0].getClassLoader());
        assertClosedAfter(spy, openedBeforeLookup, "dynfail/First.class");
        assertClosedAfter(spy, openedBeforeLookup, "dynfail/Second.class");
    }

    private static void assertClosedAfter(SpyLoader spy, int from, String resourceSuffix) {
        int seen = 0;
        for (int i = from; i < spy.opened.size(); i++) {
            NamedStream stream = spy.opened.get(i);
            if (stream.name.endsWith(resourceSuffix)) {
                seen++;
                Assert.assertTrue(stream.name, stream.closed);
            }
        }
        Assert.assertTrue(resourceSuffix + " was not opened", seen > 0);
    }

    private URLClassLoader childWebLoader() throws Exception {
        URL url = LegacyEntryStartupFailureTest.class.getProtectionDomain().getCodeSource().getLocation();
        return track(new ChildFirstWebLoader(new URL[]{url}));
    }

    private URLClassLoader suiteLoader(URL url) {
        return track(new URLClassLoader(new URL[]{url}, LegacyEntryStartupFailureTest.class.getClassLoader()));
    }

    private <T extends URLClassLoader> T track(T loader) {
        loaders.add(loader);
        return loader;
    }

    private static boolean stackContainsInitEnv(Throwable failure) {
        StackTraceElement[] stack = failure.getStackTrace();
        for (int i = 0; i < stack.length; i++) {
            if ("net.csdn.junit.IocTest".equals(stack[i].getClassName())
                    && "initEnv".equals(stack[i].getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private static File writeQuietConfig(File root, String controller, String extension) throws Exception {
        File logs = new File(root, "logs");
        Assert.assertTrue(logs.mkdirs() || logs.isDirectory());
        File config = new File(root, "application.yml");
        StringBuilder yaml = new StringBuilder();
        yaml.append("mode: test\n");
        yaml.append("cluster.name: sf-legacy-entry\n");
        yaml.append("application.token: kept\n");
        yaml.append("path.conf: ").append(quote(configDir().getAbsolutePath())).append('\n');
        yaml.append("path.logs: ").append(quote(logs.getAbsolutePath())).append('\n');
        yaml.append("test.datasources.mysql.disable: true\n");
        yaml.append("test.datasources.mongodb.disable: true\n");
        yaml.append("test.datasources.redis.disable: true\n");
        yaml.append("http.disable: true\n");
        yaml.append("thrift.disable: true\n");
        yaml.append("dubbo.disable: true\n");
        yaml.append("application.template.engine.enable: false\n");
        yaml.append("application.api.qps.enable: false\n");
        yaml.append("application.log.enable: false\n");
        yaml.append("application.controller: ").append(quote(controller)).append('\n');
        yaml.append("application.controller.default: \"\"\n");
        yaml.append("application.controllerNames: \"\"\n");
        yaml.append("application.service: \"\"\n");
        yaml.append("application.util: \"\"\n");
        yaml.append("qpslimit.enable: false\n");
        if (extension != null) {
            yaml.append("application.extensions: ").append(quote(extension)).append('\n');
        }
        write(config, yaml.toString());
        return config;
    }

    private static void rewriteTestPackage(File config, String testPackage) throws Exception {
        String source = read(config);
        write(config, source + "application.test: " + testPackage + "\n");
    }

    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
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

    private static File dynamicSuiteSource() {
        File direct = new File("test/runner/DynamicSuite.java");
        if (direct.isFile()) {
            return direct;
        }
        File parent = new File("../test/runner/DynamicSuite.java");
        if (parent.isFile()) {
            return parent;
        }
        throw new IllegalStateException("DynamicSuite.java was not found from " + new File("").getAbsolutePath());
    }

    private static void compileFixtures(File output) throws Exception {
        Assert.assertTrue(output.mkdirs() || output.isDirectory());
        File sourceDir = new File(output.getParentFile(), "src");
        File packageDir = new File(sourceDir, "dynfail");
        Assert.assertTrue(packageDir.mkdirs());
        File first = new File(packageDir, "First.java");
        File second = new File(packageDir, "Second.java");
        write(first, "package dynfail; public class First { public static int id() { return 1; } }\n");
        write(second, "package dynfail; public class Second { public static int id() { return 2; } }\n");
        compile(first, output, second);
    }

    private static void compile(File primary, File output, File... more) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Assert.assertNotNull("javac is required to probe DynamicSuite", compiler);
        StringWriter diagnostics = new StringWriter();
        StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null);
        List<File> sources = new ArrayList<File>();
        sources.add(primary);
        sources.addAll(Arrays.asList(more));
        List<String> options = new ArrayList<String>();
        options.add("-classpath");
        options.add(compilerClasspath());
        options.add("-d");
        options.add(output.getAbsolutePath());
        options.add("-encoding");
        options.add("UTF-8");
        Boolean ok = compiler.getTask(
                diagnostics,
                files,
                null,
                options,
                null,
                files.getJavaFileObjectsFromFiles(sources)).call();
        files.close();
        Assert.assertEquals(diagnostics.toString(), Boolean.TRUE, ok);
    }

    private static String compilerClasspath() throws Exception {
        StringBuilder builder = new StringBuilder();
        appendLoaderUrls(builder, LegacyEntryStartupFailureTest.class.getClassLoader());
        String raw = System.getProperty("java.class.path");
        if (raw != null && raw.length() > 0) {
            appendPath(builder, expandManifestClasspath(raw));
        }
        Assert.assertTrue("compiler classpath is empty", builder.length() > 0);
        return builder.toString();
    }

    private static void appendLoaderUrls(StringBuilder builder, ClassLoader loader) throws Exception {
        if (loader == null) {
            return;
        }
        if (loader instanceof URLClassLoader) {
            URL[] urls = ((URLClassLoader) loader).getURLs();
            for (int i = 0; i < urls.length; i++) {
                if (!"file".equals(urls[i].getProtocol())) {
                    continue;
                }
                appendPath(builder, new File(urls[i].toURI()).getAbsolutePath());
            }
        }
        appendLoaderUrls(builder, loader.getParent());
    }

    private static String expandManifestClasspath(String raw) throws Exception {
        if (raw.contains(File.pathSeparator)) {
            return raw;
        }
        File jar = new File(raw);
        if (!jar.isFile() || !jar.getName().endsWith(".jar")) {
            return raw;
        }
        JarFile jarFile = new JarFile(jar);
        try {
            Manifest manifest = jarFile.getManifest();
            if (manifest == null) {
                return raw;
            }
            String paths = manifest.getMainAttributes().getValue("Class-Path");
            if (paths == null || paths.trim().length() == 0) {
                return raw;
            }
            StringBuilder builder = new StringBuilder(jar.getAbsolutePath());
            String[] entries = paths.split(" ");
            for (int i = 0; i < entries.length; i++) {
                if (entries[i].length() == 0) {
                    continue;
                }
                File entry = manifestEntry(jar, entries[i]);
                builder.append(File.pathSeparator).append(entry.getAbsolutePath());
            }
            return builder.toString();
        } finally {
            jarFile.close();
        }
    }

    private static File manifestEntry(File jar, String entry) throws Exception {
        if (entry.startsWith("file:") || entry.startsWith("http:") || entry.startsWith("https:")) {
            return new File(new URL(entry).toURI());
        }
        return new File(jar.getParentFile(), entry);
    }

    private static void appendPath(StringBuilder builder, String path) {
        if (path == null || path.length() == 0) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(File.pathSeparator);
        }
        builder.append(path);
    }

    private static File tempRoot(String name) {
        File root = new File(System.getProperty("java.io.tmpdir"), "sf-legacy-entry-" + name + "-" + System.nanoTime());
        Assert.assertTrue(root.mkdirs() || root.isDirectory());
        return root;
    }

    private static void write(File file, String text) throws Exception {
        OutputStreamWriter writer = new OutputStreamWriter(new java.io.FileOutputStream(file), "UTF-8");
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
            return new String(bytes, 0, offset, "UTF-8");
        } finally {
            input.close();
        }
    }

    private static final class Globals {
        private String yaml;
        private ServiceFramwork.Mode mode;
        private boolean http;
        private boolean thrift;
        private boolean dubbo;
        private boolean noJoin;
        private Class loader;
        private Injector frameworkInjector;
        private Injector iocInjector;

        private static Globals capture() throws Exception {
            Globals globals = new Globals();
            globals.yaml = ServiceFramwork.applicaionYamlName();
            globals.mode = ServiceFramwork.mode;
            globals.http = flag("DisableHTTP");
            globals.thrift = flag("DisableThrift");
            globals.dubbo = flag("DisableDubbo");
            globals.noJoin = flag("NoThreadJoin");
            globals.loader = ServiceFramwork.scanService.getLoader();
            globals.frameworkInjector = ServiceFramwork.injector;
            globals.iocInjector = IocTest.injector;
            return globals;
        }

        private void restore() throws Exception {
            ServiceFramwork.applicaionYamlName(yaml);
            ServiceFramwork.mode = mode;
            setFlag("DisableHTTP", http);
            setFlag("DisableThrift", thrift);
            setFlag("DisableDubbo", dubbo);
            setFlag("NoThreadJoin", noJoin);
            ServiceFramwork.scanService.setLoader(loader);
            ServiceFramwork.injector = frameworkInjector;
            IocTest.injector = iocInjector;
        }

        private static boolean flag(String name) throws Exception {
            Field field = ServiceFramwork.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getBoolean(null);
        }

        private static void setFlag(String name, boolean value) throws Exception {
            Field field = ServiceFramwork.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(null, value);
        }
    }

    private static final class ChildFirstWebLoader extends URLClassLoader {
        private ChildFirstWebLoader(URL[] urls) {
            super(urls, LegacyEntryStartupFailureTest.class.getClassLoader());
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

    private static final class SpyLoader extends URLClassLoader {
        private final List<NamedStream> opened = new ArrayList<NamedStream>();

        private SpyLoader(URL[] urls) {
            super(urls, LegacyEntryStartupFailureTest.class.getClassLoader());
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            InputStream stream = super.getResourceAsStream(name);
            if (stream == null || name == null || !name.endsWith(".class")) {
                return stream;
            }
            NamedStream named = new NamedStream(name, stream);
            opened.add(named);
            return named;
        }
    }

    private static final class NamedStream extends FilterInputStream {
        private final String name;
        private boolean closed;

        private NamedStream(String name, InputStream input) {
            super(input);
            this.name = name;
        }

        @Override
        public void close() throws java.io.IOException {
            closed = true;
            super.close();
        }
    }
}
