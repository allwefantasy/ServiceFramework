package net.csdn.jpa;

import org.junit.Test;

import javax.sql.DataSource;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Quill initializes SLF4J when a context is constructed. This module ships the
 * API only. A binding on the application loader, or the lack of one, is not
 * replaced by a NOP binding pulled in here.
 */
public class OrmLoggingContractTest {

    @Test
    public void moduleClasspathUsesTheApiAndDoesNotForceNop() throws Exception {
        String classpath = System.getProperty("java.class.path");
        assertTrue(classpath, classpath.contains("slf4j-api-1.7.32"));
        assertFalse(classpath, classpath.contains("slf4j-nop"));
        assertFalse(classpath, classpath.contains("slf4j-api-1.7.26"));
        Enumeration<URL> binders = OrmLoggingContractTest.class.getClassLoader()
                .getResources("org/slf4j/impl/StaticLoggerBinder.class");
        while (binders.hasMoreElements()) {
            String url = binders.nextElement().toString();
            assertFalse(url, url.contains("slf4j-nop"));
        }
    }

    @Test
    public void quillInitializesWithoutABindingAndWithAConsumerBinding() throws Exception {
        List<URL> runtime = runtimeWithoutBindings();
        File output = new File(System.getProperty("java.io.tmpdir"), "sf-orm-slf4j-" + UUID.randomUUID());
        File classes = new File(output, "classes");
        assertTrue(classes.mkdirs());
        try {
            compileStub(classes);
            ByteArrayOutputStream unbound = new ByteArrayOutputStream();
            int[] without = openQuill(urls(classes, runtime), unbound);
            String fallback = unbound.toString("UTF-8");
            assertTrue(fallback, fallback.contains("StaticLoggerBinder"));
            assertTrue(fallback, fallback.contains("no-operation") || fallback.contains("NOP"));
            assertTrue(without[0] == 0 && without[1] == 0);

            compileBinding(classes, jarOf(org.slf4j.Logger.class));
            int[] withBinding = openQuill(urls(classes, runtime), null);
            assertTrue("consumer binding was not used", withBinding[0] > 0);
            assertTrue("consumer binding received no quill log event", withBinding[1] > 0);
        } finally {
            deleteQuietly(output);
        }
    }

    private static int[] openQuill(URL[] jars, ByteArrayOutputStream err) throws Exception {
        PrintStream previous = System.err;
        if (err != null) {
            System.setErr(new PrintStream(err, true, "UTF-8"));
        }
        URLClassLoader loader = new URLClassLoader(jars, platformParent());
        try {
            Class<?> contextClass = Class.forName("io.getquill.MysqlJdbcContext", true, loader);
            Class<?> naming = Class.forName("io.getquill.NamingStrategy", false, loader);
            Object snake = Class.forName("io.getquill.SnakeCase$", true, loader).getField("MODULE$").get(null);
            Object dataSource = Class.forName("net.csdn.jpa.loggingfixture.UnusedDataSource", true, loader)
                    .getDeclaredConstructor().newInstance();
            Class<?> dataSourceType = Class.forName("javax.sql.DataSource", false, loader);
            Object context = contextClass.getConstructor(naming, dataSourceType).newInstance(snake, dataSource);
            assertNotNull(context);
            assertTrue(dataSource instanceof DataSource);
            Object quillLogger = contextClass.getMethod("logger").invoke(context);
            Object scalaLogger = quillLogger.getClass().getMethod("underlying").invoke(quillLogger);
            Object slf4j = scalaLogger.getClass().getMethod("underlying").invoke(scalaLogger);
            Class<?> loggerType = Class.forName("org.slf4j.Logger", false, loader);
            Method debug = loggerType.getMethod("debug", String.class);
            debug.setAccessible(true);
            debug.invoke(slf4j, "quill ready");
            return bindingCounts(loader);
        } finally {
            loader.close();
            if (err != null) {
                System.setErr(previous);
            }
        }
    }

    private static URL[] urls(File classes, List<URL> runtime) throws Exception {
        List<URL> urls = new ArrayList<URL>();
        urls.add(classes.toURI().toURL());
        urls.addAll(runtime);
        return urls.toArray(new URL[urls.size()]);
    }

    private static List<URL> runtimeWithoutBindings() throws Exception {
        List<URL> urls = new ArrayList<URL>();
        String[] entries = System.getProperty("java.class.path").split(File.pathSeparator);
        for (int i = 0; i < entries.length; i++) {
            if (entries[i].length() == 0) {
                continue;
            }
            File file = new File(entries[i]);
            if (!file.exists() || containsBinder(file)) {
                continue;
            }
            urls.add(file.toURI().toURL());
        }
        assertFalse(urls.isEmpty());
        return urls;
    }

    private static boolean containsBinder(File file) throws Exception {
        if (file.isDirectory()) {
            return new File(file, "org/slf4j/impl/StaticLoggerBinder.class").isFile();
        }
        ZipFile zip;
        try {
            zip = new ZipFile(file);
        } catch (ZipException notAJar) {
            return false;
        }
        try {
            return zip.getEntry("org/slf4j/impl/StaticLoggerBinder.class") != null;
        } finally {
            zip.close();
        }
    }

    private static int[] bindingCounts(ClassLoader loader) throws Exception {
        Class<?> factory;
        try {
            factory = Class.forName("org.slf4j.impl.CountingFactory", false, loader);
        } catch (ClassNotFoundException missing) {
            return new int[]{0, 0};
        }
        int loggers = ((AtomicInteger) readStatic(factory, "LOGGERS")).get();
        Class<?> logger = Class.forName("org.slf4j.impl.CountingLogger", false, loader);
        int events = ((AtomicInteger) readStatic(logger, "EVENTS")).get();
        return new int[]{loggers, events};
    }

    private static Object readStatic(Class<?> type, String name) throws Exception {
        Field field = type.getField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void compileStub(File classes) throws Exception {
        File sourceRoot = new File(classes.getParentFile(), "stub-src");
        File packageDir = new File(sourceRoot, "net/csdn/jpa/loggingfixture");
        assertTrue(packageDir.mkdirs());
        File source = new File(packageDir, "UnusedDataSource.java");
        Files.write(source.toPath(), stubSource().getBytes(Charset.forName("UTF-8")));
        compile(source, classes, null);
    }

    private static void compileBinding(File classes, URL api) throws Exception {
        File sourceRoot = new File(classes.getParentFile(), "binding-src");
        File packageDir = new File(sourceRoot, "org/slf4j/impl");
        assertTrue(packageDir.mkdirs());
        File source = new File(packageDir, "StaticLoggerBinder.java");
        Files.write(source.toPath(), bindingSource().getBytes(Charset.forName("UTF-8")));
        compile(source, classes, new File(api.toURI()).getAbsolutePath());
    }

    private static void compile(File source, File classes, String classpath) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            fail("this JDK has no compiler");
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();
        StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, null, null);
        try {
            List<String> options = new ArrayList<String>();
            options.add("-encoding");
            options.add("UTF-8");
            options.add("-d");
            options.add(classes.getAbsolutePath());
            if (classpath != null) {
                options.add("-classpath");
                options.add(classpath);
            }
            String specification = System.getProperty("java.specification.version");
            if ("1.8".equals(specification)) {
                options.add("-source");
                options.add("8");
                options.add("-target");
                options.add("8");
            } else {
                options.add("--release");
                options.add("8");
            }
            Boolean ok = compiler.getTask(
                    null,
                    files,
                    diagnostics,
                    options,
                    null,
                    files.getJavaFileObjects(source)).call();
            if (!Boolean.TRUE.equals(ok)) {
                fail("compile failed " + diagnostics.getDiagnostics());
            }
        } finally {
            files.close();
        }
    }

    private static String stubSource() {
        return "package net.csdn.jpa.loggingfixture;\n"
                + "import java.io.PrintWriter;\n"
                + "import java.sql.Connection;\n"
                + "import java.sql.SQLException;\n"
                + "import java.sql.SQLFeatureNotSupportedException;\n"
                + "import java.util.logging.Logger;\n"
                + "import javax.sql.DataSource;\n"
                + "public class UnusedDataSource implements DataSource {\n"
                + "  public Connection getConnection() throws SQLException { throw new SQLException(\"unused\"); }\n"
                + "  public Connection getConnection(String user, String password) throws SQLException { throw new SQLException(\"unused\"); }\n"
                + "  public PrintWriter getLogWriter() { return null; }\n"
                + "  public void setLogWriter(PrintWriter out) {}\n"
                + "  public void setLoginTimeout(int seconds) {}\n"
                + "  public int getLoginTimeout() { return 0; }\n"
                + "  public Logger getParentLogger() throws SQLFeatureNotSupportedException { throw new SQLFeatureNotSupportedException(); }\n"
                + "  public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException(\"unused\"); }\n"
                + "  public boolean isWrapperFor(Class<?> iface) { return false; }\n"
                + "}\n";
    }

    private static String bindingSource() {
        StringBuilder source = new StringBuilder();
        source.append("package org.slf4j.impl;\n");
        source.append("import org.slf4j.ILoggerFactory;\n");
        source.append("import org.slf4j.Logger;\n");
        source.append("import org.slf4j.helpers.MarkerIgnoringBase;\n");
        source.append("import java.util.concurrent.atomic.AtomicInteger;\n");
        source.append("public class StaticLoggerBinder {\n");
        source.append("  private static final StaticLoggerBinder SINGLETON = new StaticLoggerBinder();\n");
        source.append("  public static final String REQUESTED_API_VERSION = \"1.7.32\";\n");
        source.append("  private final ILoggerFactory factory = new CountingFactory();\n");
        source.append("  public static StaticLoggerBinder getSingleton() { return SINGLETON; }\n");
        source.append("  public ILoggerFactory getLoggerFactory() { return factory; }\n");
        source.append("  public String getLoggerFactoryClassStr() { return CountingFactory.class.getName(); }\n");
        source.append("}\n");
        source.append("class CountingFactory implements ILoggerFactory {\n");
        source.append("  public static final AtomicInteger LOGGERS = new AtomicInteger();\n");
        source.append("  public Logger getLogger(String name) {\n");
        source.append("    LOGGERS.incrementAndGet();\n");
        source.append("    return new CountingLogger(name);\n");
        source.append("  }\n");
        source.append("}\n");
        source.append("class CountingLogger extends MarkerIgnoringBase {\n");
        source.append("  public static final AtomicInteger EVENTS = new AtomicInteger();\n");
        source.append("  CountingLogger(String name) { this.name = name; }\n");
        source.append("  private void mark() { EVENTS.incrementAndGet(); }\n");
        String[] levels = new String[]{"trace", "debug", "info", "warn", "error"};
        for (int i = 0; i < levels.length; i++) {
            String level = levels[i];
            String enabled = "is" + Character.toUpperCase(level.charAt(0)) + level.substring(1) + "Enabled";
            source.append("  public boolean ").append(enabled).append("() { return true; }\n");
            source.append("  public void ").append(level).append("(String msg) { mark(); }\n");
            source.append("  public void ").append(level).append("(String format, Object arg) { mark(); }\n");
            source.append("  public void ").append(level).append("(String format, Object arg1, Object arg2) { mark(); }\n");
            source.append("  public void ").append(level).append("(String format, Object[] arguments) { mark(); }\n");
            source.append("  public void ").append(level).append("(String msg, Throwable t) { mark(); }\n");
        }
        source.append("}\n");
        return source.toString();
    }

    private static ClassLoader platformParent() throws Exception {
        try {
            Method method = ClassLoader.class.getMethod("getPlatformClassLoader");
            return (ClassLoader) method.invoke(null);
        } catch (NoSuchMethodException missing) {
            return ClassLoader.getSystemClassLoader().getParent();
        }
    }

    private static URL jarOf(Class<?> type) {
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            fail("no code source for " + type.getName());
        }
        return source.getLocation();
    }

    private static void deleteQuietly(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (int i = 0; i < children.length; i++) {
                    deleteQuietly(children[i]);
                }
            }
        }
        file.delete();
    }
}
