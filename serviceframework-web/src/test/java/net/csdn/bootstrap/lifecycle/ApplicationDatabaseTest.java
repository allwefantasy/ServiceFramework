package net.csdn.bootstrap.lifecycle;

import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.Bootstrap;
import net.csdn.bootstrap.loader.impl.DocumentLoader;
import net.csdn.bootstrap.loader.impl.ModelLoader;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bootstrap HTTP against the isolated compat MySQL and MongoDB.
 * Skipped unless {@code SF_COMPAT_WEB_DB=true} or {@code -Dsf.compat.web.db=true}.
 * An enabled switch with no {@code SF_COMPAT_ENV_FILE} fails instead of skipping.
 */
public class ApplicationDatabaseTest {
    private static final String ORM = "net.csdn.bootstrap.lifecycle.web.db.orm";
    private static final String MONGO = "net.csdn.bootstrap.lifecycle.web.db.mongo";
    private static final String BOTH = "net.csdn.bootstrap.lifecycle.web.db.both";
    private static final String BAD = "net.csdn.bootstrap.lifecycle.web.db.bad";
    private static final String EMPTY = "net.csdn.bootstrap.lifecycle.web.db.empty";

    private final List<ApplicationContext> contexts = new ArrayList<ApplicationContext>();
    private final List<URLClassLoader> loaders = new ArrayList<URLClassLoader>();
    private Map<String, String> env;

    @Before
    public void requireServices() throws Exception {
        Assume.assumeTrue(
                "SF_COMPAT_WEB_DB is not enabled; a skip is not acceptance of the database path",
                webDbRequested());
        String path = System.getenv("SF_COMPAT_ENV_FILE");
        if (path == null || path.length() == 0) {
            Assert.fail("SF_COMPAT_WEB_DB is enabled but SF_COMPAT_ENV_FILE is missing");
        }
        env = readEnv(new File(path));
        requireKey("SF_COMPAT_MYSQL_HOST");
        requireKey("SF_COMPAT_MYSQL_PORT");
        requireKey("SF_COMPAT_MYSQL_USER");
        requireKey("SF_COMPAT_MYSQL_PASSWORD");
        requireKey("SF_COMPAT_MYSQL_DATABASE");
        requireKey("SF_COMPAT_MONGO_HOST");
        requireKey("SF_COMPAT_MONGO_PORT");
        requireKey("SF_COMPAT_MONGO_USER");
        requireKey("SF_COMPAT_MONGO_PASSWORD");
        requireKey("SF_COMPAT_MONGO_AUTH_DB");
        requireKey("SF_COMPAT_MONGO_DATABASE");
        requireKey("SF_COMPAT_MONGO_REPLSET");
        if (!"sf_compat".equals(env.get("SF_COMPAT_MYSQL_DATABASE"))) {
            Assert.fail("refusing a MySQL database other than sf_compat");
        }
        if (!"sf_compat".equals(env.get("SF_COMPAT_MONGO_DATABASE"))) {
            Assert.fail("refusing a MongoDB database other than sf_compat");
        }
    }

    @After
    public void tearDown() throws Exception {
        for (int i = contexts.size() - 1; i >= 0; i--) {
            closeWhenIdle(contexts.get(i));
        }
        for (int i = 0; i < loaders.size(); i++) {
            loaders.get(i).close();
        }
        contexts.clear();
        loaders.clear();
        if (env != null) {
            try {
                dropTable();
            } catch (Exception ignored) {
                // Schema cleanup must not hide the assertion.
            }
            try {
                dropCollection();
            } catch (Exception ignored) {
                // Schema cleanup must not hide the assertion.
            }
        }
    }

    @Test
    public void ormEnabledMongoDisabledRoundTripsThroughHttp() throws Exception {
        createTable();
        String label = "orm-" + System.nanoTime();
        URLClassLoader loader = isolated();
        ApplicationContext context = start(loader, "orm-only", ORM, ORM, "", true, false);
        EnhancementContext.Scope scope = context.activate();
        try {
            new ModelLoader().load(context.settings());
            Assert.assertSame(context.injector(), net.csdn.ServiceFramwork.currentInjector());
        } finally {
            scope.close();
        }
        HttpResult result = http(context.httpPort(), "/db/orm?q=" + url(label));
        Assert.assertEquals(result.body, 200, result.status);
        Assert.assertEquals(label + ":orm-only", result.body);
        closeWhenIdle(context);
        assertRefused(context.httpPort());
    }

    @Test
    public void mongoEnabledOrmDisabledRoundTripsThroughHttp() throws Exception {
        String text = "mongo-" + System.nanoTime();
        URLClassLoader loader = isolated();
        ApplicationContext context = start(loader, "mongo-only", MONGO, "", MONGO, false, true);
        HttpResult result = http(context.httpPort(), "/db/mongo?q=" + url(text));
        Assert.assertEquals(result.body, 200, result.status);
        Assert.assertEquals(text + ":mongo-only", result.body);
        closeWhenIdle(context);
        assertRefused(context.httpPort());
    }

    @Test
    public void bothDatabasesRoundTripThroughHttp() throws Exception {
        createTable();
        String value = "both-" + System.nanoTime();
        URLClassLoader loader = isolated();
        ApplicationContext context = start(loader, "both", BOTH, ORM, MONGO, true, true);
        HttpResult result = http(context.httpPort(), "/db/both?q=" + url(value));
        Assert.assertEquals(result.body, 200, result.status);
        Assert.assertEquals(value + ":" + value + ":both", result.body);
        closeWhenIdle(context);
        assertRefused(context.httpPort());
    }

    @Test
    public void modelLoaderDefinesThroughTheApplicationContext() throws Exception {
        createTable();
        URLClassLoader loader = isolated();
        Class<?> marker = marker(loader, ORM + ".ServiceFrameworkPackageAnchor");
        ApplicationContext context = track(ApplicationContext.open(marker));
        Settings settings = loaderSettings("loader-orm", ORM, "", true, false);
        EnhancementContext.Scope scope = context.activate();
        try {
            new ModelLoader().load(settings);
            JPA.getJPAConfig();
            Class<?> type = Class.forName(ORM + ".WebRecord", true, loader);
            Assert.assertSame(loader, type.getClassLoader());
            Object record = type.getDeclaredConstructor().newInstance();
            String label = "loader-" + System.nanoTime();
            type.getMethod("setLabel", String.class).invoke(record, label);
            Assert.assertEquals(Boolean.TRUE, type.getMethod("save").invoke(record));
            Object id = type.getMethod("getId").invoke(record);
            Object found = type.getMethod("findById", Object.class).invoke(null, id);
            Assert.assertEquals(label, found.getClass().getMethod("getLabel").invoke(found));
            try {
                new ModelLoader().load(settings);
            } catch (EnhancementFailure failure) {
                Assert.fail("repeated ModelLoader did not stay on the configured context: " + failure.getMessage());
            }
        } finally {
            scope.close();
        }
        context.close();
        Assert.assertTrue(context.isClosed());
    }

    @Test
    public void documentLoaderDefinesThroughTheApplicationContext() throws Exception {
        URLClassLoader loader = isolated();
        Class<?> marker = marker(loader, MONGO + ".ServiceFrameworkPackageAnchor");
        ApplicationContext context = track(ApplicationContext.open(marker));
        Settings settings = loaderSettings("loader-mongo", "", MONGO, false, true);
        String text = "doc-" + System.nanoTime();
        EnhancementContext.Scope scope = context.activate();
        try {
            new DocumentLoader().load(settings);
            Class<?> type = Class.forName(MONGO + ".WebNote", true, loader);
            Assert.assertSame(loader, type.getClassLoader());
            Object note = type.getDeclaredConstructor().newInstance();
            type.getMethod("id", Object.class).invoke(note, text);
            type.getMethod("setText", String.class).invoke(note, text);
            Assert.assertEquals(Boolean.TRUE, type.getMethod("insert").invoke(note));
            Object found = type.getMethod("findById", Object.class).invoke(null, text);
            Assert.assertNotNull(found);
            Assert.assertEquals(text, type.getMethod("getText").invoke(found));
            try {
                new DocumentLoader().load(settings);
                Assert.fail("second DocumentLoader redefined the loader");
            } catch (EnhancementFailure failure) {
                Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("hot reload")
                        || failure.getMessage().contains("already"));
            }
        } finally {
            scope.close();
        }
        context.close();
        Assert.assertTrue(context.isClosed());
    }

    @Test
    public void missingEntitiesFailBeforeThePortOpens() throws Exception {
        int port = freePort();
        URLClassLoader loader = isolated();
        try {
            start(loader, "empty", EMPTY, EMPTY, "", true, false, port);
            Assert.fail("an empty model package started HTTP");
        } catch (EnhancementFailure failure) {
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("no entity"));
        }
        assertFree(port);
        assertNoLeftoverWorkers();
    }

    @Test
    public void illegalAssociationFailsBeforeThePortOpens() throws Exception {
        int port = freePort();
        URLClassLoader loader = isolated();
        try {
            start(loader, "bad-assoc", BAD, BAD, "", true, false, port);
            Assert.fail("an illegal association started HTTP");
        } catch (EnhancementFailure failure) {
            String message = failure.getMessage();
            Assert.assertTrue(message, message.contains("NotAnEntity")
                    || message.contains("BrokenOrder")
                    || message.contains("association")
                    || message.contains("EntityManagerFactory")
                    || message.contains("customer"));
        }
        assertFree(port);
        assertNoLeftoverWorkers();
    }

    @Test
    public void wrongMysqlPasswordFailsBeforeThePortOpens() throws Exception {
        int port = freePort();
        URLClassLoader loader = isolated();
        String secret = env.get("SF_COMPAT_MYSQL_PASSWORD");
        try {
            Settings settings = databaseSettings("bad-password", BAD, ORM, "", true, false)
                    .put("http.disable", "false")
                    .put("http.port", String.valueOf(port))
                    .put("test.datasources.mysql.password", "not-the-compat-password")
                    .build();
            track(Bootstrap.configureSystem(settings, marker(loader, ORM + ".ServiceFrameworkPackageAnchor")));
            Assert.fail("a rejected MySQL password started HTTP");
        } catch (EnhancementFailure failure) {
            Assert.assertFalse(failure.getMessage(), failure.getMessage().contains(secret));
        }
        assertFree(port);
        assertNoLeftoverWorkers();
    }

    @Test
    public void mongoConnectionFailureHappensBeforeThePortOpens() throws Exception {
        int port = freePort();
        URLClassLoader loader = isolated();
        try {
            Settings settings = databaseSettings("bad-mongo", MONGO, "", MONGO, false, true)
                    .put("http.disable", "false")
                    .put("http.port", String.valueOf(port))
                    .put("test.datasources.mongodb.port", "1")
                    .build();
            track(Bootstrap.configureSystem(settings, marker(loader, MONGO + ".ServiceFrameworkPackageAnchor")));
            Assert.fail("a refused MongoDB port started HTTP");
        } catch (EnhancementFailure failure) {
            Assert.assertTrue(failure.getMessage(), failure.getMessage().length() > 0);
            Assert.assertFalse(failure.getMessage(), failure.getMessage().contains(env.get("SF_COMPAT_MONGO_PASSWORD")));
        }
        assertFree(port);
        assertNoLeftoverWorkers();
    }

    private ApplicationContext start(
            URLClassLoader loader,
            String token,
            String controller,
            String model,
            String document,
            boolean mysql,
            boolean mongo) throws Exception {
        return start(loader, token, controller, model, document, mysql, mongo, 0);
    }

    private ApplicationContext start(
            URLClassLoader loader,
            String token,
            String controller,
            String model,
            String document,
            boolean mysql,
            boolean mongo,
            int port) throws Exception {
        String anchorPackage = controller;
        Settings settings = databaseSettings(token, controller, model, document, mysql, mongo)
                .put("http.disable", "false")
                .put("http.port", String.valueOf(port))
                .build();
        return track(Bootstrap.configureSystem(settings, marker(loader, anchorPackage + ".ServiceFrameworkPackageAnchor")));
    }

    /**
     * {@link ApplicationContext#open} has not run {@code start}, so its mode is
     * still development. Datasource keys have to use that prefix.
     */
    private Settings loaderSettings(String token, String model, String document, boolean mysql, boolean mongo) {
        return DatabaseFixtureSupport.loaderSettings(env, token, model, document, mysql, mongo).build();
    }

    private ImmutableSettings.Builder databaseSettings(
            String token,
            String controller,
            String model,
            String document,
            boolean mysql,
            boolean mongo) {
        return DatabaseFixtureSupport.databaseSettings(env, token, controller, model, document, mysql, mongo);
    }

    private void createTable() throws Exception {
        DatabaseFixtureSupport.createTable(env);
    }

    private void dropTable() throws Exception {
        DatabaseFixtureSupport.dropTable(env);
    }

    private void dropCollection() throws Exception {
        DatabaseFixtureSupport.dropCollection(env);
    }

    private static void closeWhenIdle(ApplicationContext context) {
        DatabaseFixtureSupport.closeWhenIdle(context);
    }

    private ApplicationContext track(ApplicationContext context) {
        contexts.add(context);
        return context;
    }

    private URLClassLoader isolated() throws Exception {
        return DatabaseFixtureSupport.isolated(loaders);
    }

    private static Class<?> marker(URLClassLoader loader, String name) throws Exception {
        return DatabaseFixtureSupport.marker(loader, name);
    }

    private static boolean webDbRequested() {
        return DatabaseFixtureSupport.webDbRequested();
    }

    private void requireKey(String key) {
        String value = env.get(key);
        if (value == null || value.length() == 0) {
            Assert.fail("compat env is missing " + key);
        }
    }

    private static Map<String, String> readEnv(File file) throws Exception {
        return DatabaseFixtureSupport.readEnv(file);
    }

    private static String url(String value) throws Exception {
        return DatabaseFixtureSupport.url(value);
    }

    private static HttpResult http(int port, String path) throws Exception {
        DatabaseFixtureSupport.HttpResult result = DatabaseFixtureSupport.http(port, path);
        return new HttpResult(result.status, result.body);
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

    private static final class HttpResult {
        private final int status;
        private final String body;

        private HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
