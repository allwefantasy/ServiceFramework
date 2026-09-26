package net.csdn.mongo.enhancer;

import org.junit.Assume;
import org.junit.Test;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The driver is exercised in class loaders that do not see this module's test
 * classpath, so a binding chosen by the application (or the absence of one)
 * is not confused with a binding this module might have pulled in.
 */
public class MongoDriverLoggingLiveTest {

    @Test
    public void moduleClasspathDoesNotForceNopBinding() throws Exception {
        Enumeration<URL> binders = MongoDriverLoggingLiveTest.class.getClassLoader()
                .getResources("org/slf4j/impl/StaticLoggerBinder.class");
        while (binders.hasMoreElements()) {
            String url = binders.nextElement().toString();
            assertFalse(url, url.contains("slf4j-nop"));
        }
    }

    @Test
    public void driverConnectsWithoutApiWithoutBindingAndWithTestBinding() throws Exception {
        Assume.assumeTrue(
                "sf.compat.mongo is not enabled",
                MongoEnhancementLiveTest.mongoRequested());
        Map<String, String> env = MongoEnhancementLiveTest.credentials();
        URL[] driver = driverJars();
        URL api = jarOf(org.slf4j.Logger.class);
        connect(driver, env, false, null);
        ByteArrayOutputStream unbound = new ByteArrayOutputStream();
        connect(concat(driver, api), env, true, unbound);
        String fallback = unbound.toString("UTF-8");
        assertTrue(fallback, fallback.contains("StaticLoggerBinder"));
        assertTrue(fallback, fallback.contains("no-operation") || fallback.contains("NOP"));
        File binding = compileBinding(api);
        try {
            int[] counts = connect(concat(driver, binding.toURI().toURL(), api), env, true, null);
            assertTrue("test binding was not used", counts[0] > 0);
            assertTrue("test binding received no driver log events", counts[1] > 0);
        } finally {
            deleteQuietly(binding.getParentFile());
        }
    }

    /**
     * The 5.x driver is split across four artifacts; all of them are needed for
     * a connection and none may carry an slf4j binding or the slf4j API.
     */
    private static URL[] driverJars() {
        URL[] jars = new URL[]{
                jarOf(com.mongodb.MongoClient.class),
                jarOf(com.mongodb.MongoClientSettings.class),
                jarOf(com.mongodb.client.MongoClients.class),
                jarOf(org.bson.BsonDocument.class)
        };
        for (int i = 0; i < jars.length; i++) {
            assertFalse(jars[i].toString(), jars[i].toString().contains("slf4j"));
        }
        return jars;
    }

    private static URL[] concat(URL[] head, URL... tail) {
        URL[] all = new URL[head.length + tail.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(tail, 0, all, head.length, tail.length);
        return all;
    }

    /**
     * @return {@code [loggers created, log events]} when a counting binding is on the loader; otherwise zeros
     */
    private static int[] connect(URL[] jars, Map<String, String> env, boolean expectApi, ByteArrayOutputStream err) throws Exception {
        PrintStream previous = System.err;
        if (err != null) {
            System.setErr(new PrintStream(err, true, "UTF-8"));
        }
        URLClassLoader loader = new URLClassLoader(jars, platformParent());
        try {
            Class<?> logger = null;
            try {
                logger = Class.forName("org.slf4j.Logger", false, loader);
            } catch (ClassNotFoundException missing) {
                logger = null;
            }
            if (expectApi) {
                assertTrue("slf4j-api was not visible to the isolated loader", logger != null);
            } else {
                assertTrue("driver-only loader can see slf4j-api", logger == null);
            }
            Class<?> clientClass = Class.forName("com.mongodb.MongoClient", true, loader);
            Class<?> credentialClass = Class.forName("com.mongodb.MongoCredential", false, loader);
            Class<?> addressClass = Class.forName("com.mongodb.ServerAddress", false, loader);
            Class<?> optionsClass = Class.forName("com.mongodb.MongoClientOptions", false, loader);
            Class<?> commandClass = Class.forName("com.mongodb.BasicDBObject", false, loader);
            Class<?> dbObject = Class.forName("com.mongodb.DBObject", false, loader);
            Object builder = optionsClass.getMethod("builder").invoke(null);
            builder.getClass().getMethod("serverSelectionTimeout", int.class).invoke(builder, Integer.valueOf(8000));
            builder.getClass().getMethod("connectTimeout", int.class).invoke(builder, Integer.valueOf(8000));
            builder.getClass().getMethod("socketTimeout", int.class).invoke(builder, Integer.valueOf(20000));
            builder.getClass().getMethod("requiredReplicaSetName", String.class)
                    .invoke(builder, env.get("SF_COMPAT_MONGO_REPLSET"));
            Object options = builder.getClass().getMethod("build").invoke(builder);
            Object address = addressClass.getConstructor(String.class, int.class).newInstance(
                    env.get("SF_COMPAT_MONGO_HOST"),
                    Integer.valueOf(env.get("SF_COMPAT_MONGO_PORT")));
            Object credential = credentialClass.getMethod(
                    "createScramSha256Credential", String.class, String.class, char[].class).invoke(
                    null,
                    env.get("SF_COMPAT_MONGO_USER"),
                    env.get("SF_COMPAT_MONGO_AUTH_DB"),
                    env.get("SF_COMPAT_MONGO_PASSWORD").toCharArray());
            Object client = clientClass.getConstructor(addressClass, credentialClass, optionsClass)
                    .newInstance(address, credential, options);
            try {
                Object database = clientClass.getMethod("getDB", String.class)
                        .invoke(client, env.get("SF_COMPAT_MONGO_DATABASE"));
                Object command = commandClass.getConstructor(String.class, Object.class)
                        .newInstance("ping", Integer.valueOf(1));
                Object result = database.getClass().getMethod("command", dbObject).invoke(database, command);
                Object ok = result.getClass().getMethod("ok").invoke(result);
                assertTrue("mongo ping was not ok", Boolean.TRUE.equals(ok));
            } finally {
                clientClass.getMethod("close").invoke(client);
            }
            return bindingCounts(loader);
        } finally {
            loader.close();
            if (err != null) {
                System.setErr(previous);
            }
        }
    }

    private static int[] bindingCounts(ClassLoader loader) throws Exception {
        Class<?> factory;
        try {
            factory = Class.forName("org.slf4j.impl.CountingFactory", false, loader);
        } catch (ClassNotFoundException missing) {
            return new int[]{0, 0};
        }
        int loggers = counter(factory, "LOGGERS");
        Class<?> logger = Class.forName("org.slf4j.impl.CountingLogger", false, loader);
        int events = counter(logger, "EVENTS");
        return new int[]{loggers, events};
    }

    private static int counter(Class<?> type, String fieldName) throws Exception {
        Field field = type.getField(fieldName);
        field.setAccessible(true);
        return ((AtomicInteger) field.get(null)).get();
    }

    private static File compileBinding(URL api) throws Exception {
        File output = new File(System.getProperty("java.io.tmpdir"), "sf-mongo-slf4j-" + UUID.randomUUID());
        File sourceRoot = new File(output, "src");
        File classes = new File(output, "classes");
        assertTrue(new File(sourceRoot, "org/slf4j/impl").mkdirs());
        assertTrue(classes.mkdirs());
        File source = new File(sourceRoot, "org/slf4j/impl/StaticLoggerBinder.java");
        Files.write(source.toPath(), bindingSource().getBytes(Charset.forName("UTF-8")));
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
            options.add("-classpath");
            options.add(new File(api.toURI()).getAbsolutePath());
            options.add("-d");
            options.add(classes.getAbsolutePath());
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
                fail("binding compile failed " + diagnostics.getDiagnostics());
            }
        } finally {
            files.close();
        }
        return classes;
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
