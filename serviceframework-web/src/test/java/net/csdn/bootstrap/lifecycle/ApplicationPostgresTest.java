package net.csdn.bootstrap.lifecycle;

import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.Bootstrap;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import net.csdn.bootstrap.loader.impl.ModelLoader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL-only web ORM gate. Enabled by {@code SF_WEB_PG=true} or
 * {@code -Dsf.web.pg=true} and requires {@code SF_COMPAT_PG_ENV_FILE}.
 */
public class ApplicationPostgresTest {
    private static final String ORM = "net.csdn.bootstrap.lifecycle.web.db.orm";

    private final List<ApplicationContext> contexts = new ArrayList<ApplicationContext>();
    private final List<URLClassLoader> loaders = new ArrayList<URLClassLoader>();
    private Map<String, String> env;

    @Before
    public void requirePostgres() throws Exception {
        Assume.assumeTrue(
                "SF_WEB_PG is not enabled; a skip is not acceptance of the PostgreSQL web path",
                DatabaseFixtureSupport.webPgRequested());
        String path = System.getenv("SF_COMPAT_PG_ENV_FILE");
        if (path == null || path.length() == 0) {
            Assert.fail("SF_WEB_PG is enabled but SF_COMPAT_PG_ENV_FILE is missing");
        }
        env = DatabaseFixtureSupport.readEnv(new File(path));
        requireKey("SF_COMPAT_PG_HOST");
        requireKey("SF_COMPAT_PG_PORT");
        requireKey("SF_COMPAT_PG_USER");
        requireKey("SF_COMPAT_PG_PASSWORD");
        requireKey("SF_COMPAT_PG_DATABASE");
        if (!"sf_compat".equals(env.get("SF_COMPAT_PG_DATABASE"))) {
            Assert.fail("refusing a PostgreSQL database other than sf_compat");
        }
        DatabaseFixtureSupport.createPostgresTable(env);
    }

    @After
    public void tearDown() throws Exception {
        for (int i = contexts.size() - 1; i >= 0; i--) {
            DatabaseFixtureSupport.closeWhenIdle(contexts.get(i));
        }
        for (int i = 0; i < loaders.size(); i++) {
            loaders.get(i).close();
        }
        contexts.clear();
        loaders.clear();
        if (env != null) {
            try {
                DatabaseFixtureSupport.dropPostgresTable(env);
            } catch (Exception ignored) {
                // Cleanup must not hide the assertion.
            }
        }
    }

    @Test
    public void postgresOrmRoundTripsThroughHttp() throws Exception {
        String label = "pg-orm-" + System.nanoTime();
        URLClassLoader loader = DatabaseFixtureSupport.isolated(loaders);
        Settings settings = DatabaseFixtureSupport.postgresSettings(env, "pg-only", ORM, ORM)
                .put("http.disable", "false")
                .put("http.port", "0")
                .build();
        ApplicationContext context = track(Bootstrap.configureSystem(
                settings,
                DatabaseFixtureSupport.marker(loader, ORM + ".ServiceFrameworkPackageAnchor")));
        DatabaseFixtureSupport.HttpResult result = DatabaseFixtureSupport.http(
                context.httpPort(), "/db/orm?q=" + DatabaseFixtureSupport.url(label));
        Assert.assertEquals(result.body, 200, result.status);
        Assert.assertEquals(label + ":pg-only", result.body);
        assertPostgresLabel(label);
        DatabaseFixtureSupport.closeWhenIdle(context);
        assertRefused(context.httpPort());
    }

    @Test
    public void modelLoaderWorksOnPostgresPrimary() throws Exception {
        URLClassLoader loader = DatabaseFixtureSupport.isolated(loaders);
        Class<?> marker = DatabaseFixtureSupport.marker(loader, ORM + ".ServiceFrameworkPackageAnchor");
        ApplicationContext context = track(ApplicationContext.open(marker));
        Settings settings = loaderSettings();
        EnhancementContext.Scope scope = context.activate();
        try {
            new ModelLoader().load(settings);
            Class<?> type = Class.forName(ORM + ".WebRecord", true, loader);
            Object record = type.getDeclaredConstructor().newInstance();
            String label = "pg-loader-" + System.nanoTime();
            type.getMethod("setLabel", String.class).invoke(record, label);
            Assert.assertEquals(Boolean.TRUE, type.getMethod("save").invoke(record));
            Object id = type.getMethod("getId").invoke(record);
            Object found = type.getMethod("findById", Object.class).invoke(null, id);
            Assert.assertEquals(label, found.getClass().getMethod("getLabel").invoke(found));
        } finally {
            scope.close();
        }
        context.close();
        Assert.assertTrue(context.isClosed());
    }

    private void assertPostgresLabel(String label) throws Exception {
        Connection connection = DatabaseFixtureSupport.openPostgres(env);
        try {
            PreparedStatement statement = connection.prepareStatement(
                    "SELECT label FROM \"" + DatabaseFixtureSupport.TABLE + "\" WHERE label=?");
            try {
                statement.setString(1, label);
                ResultSet rows = statement.executeQuery();
                try {
                    Assert.assertTrue("HTTP save was not committed", rows.next());
                    Assert.assertEquals(label, rows.getString(1));
                    Assert.assertFalse(rows.next());
                } finally {
                    rows.close();
                }
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    private Settings loaderSettings() {
        String schema = env.get("SF_COMPAT_PG_SCHEMA");
        if (schema == null || schema.length() == 0) {
            schema = "public";
        }
        return ImmutableSettings.settingsBuilder()
                .put("mode", "development")
                .put("path.conf", DatabaseFixtureSupport.configDir().getAbsolutePath())
                .put("path.logs", new File("target/logs").getAbsolutePath())
                .put("cluster.name", "sf-web-postgres")
                .put("application.token", "pg-loader")
                .put("application.model", ORM)
                .put("application.document", "")
                .put("development.datasources.primary", "postgres")
                .put("development.datasources.postgres.host", env.get("SF_COMPAT_PG_HOST"))
                .put("development.datasources.postgres.port", env.get("SF_COMPAT_PG_PORT"))
                .put("development.datasources.postgres.database", env.get("SF_COMPAT_PG_DATABASE"))
                .put("development.datasources.postgres.schema", schema)
                .put("development.datasources.postgres.username", env.get("SF_COMPAT_PG_USER"))
                .put("development.datasources.postgres.password", env.get("SF_COMPAT_PG_PASSWORD"))
                .put("development.datasources.postgres.show_sql", "false")
                .put("development.datasources.postgres.initialSize", "1")
                .put("development.datasources.postgres.minIdle", "0")
                .put("development.datasources.postgres.maxActive", "2")
                .put("development.datasources.postgres.jdbc.connectTimeout", "5000")
                .build();
    }

    private ApplicationContext track(ApplicationContext context) {
        contexts.add(context);
        return context;
    }

    private void requireKey(String key) {
        String value = env.get(key);
        if (value == null || value.length() == 0) {
            Assert.fail("compat env is missing " + key);
        }
    }

    private static void assertRefused(int port) throws Exception {
        long deadline = System.currentTimeMillis() + 4000;
        while (System.currentTimeMillis() < deadline) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress("127.0.0.1", port), 300);
            } catch (Exception expected) {
                return;
            } finally {
                socket.close();
            }
            Thread.sleep(100);
        }
        Assert.fail("HTTP port is still accepting " + port);
    }
}
