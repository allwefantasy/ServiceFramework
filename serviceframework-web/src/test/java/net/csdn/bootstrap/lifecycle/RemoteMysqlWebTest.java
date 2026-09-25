package net.csdn.bootstrap.lifecycle;

import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.Bootstrap;
import net.csdn.common.settings.Settings;
import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Starts the HTTP service and round-trips one row through RemoteService MySQL.
 * Skipped unless {@code SF_REMOTE_MYSQL=true}. The database must be
 * {@code sf_serviceframework_e2e} and the account {@code sf_e2e}; other names fail.
 * A loopback host is only valid as an SSH forward to that server.
 */
public class RemoteMysqlWebTest {
    private static final String DATABASE = "sf_serviceframework_e2e";
    private static final String USER = "sf_e2e";
    private static final String PACKAGE = "net.csdn.bootstrap.lifecycle.web.db.orm";

    private final List<ApplicationContext> contexts = new ArrayList<ApplicationContext>();
    private final List<URLClassLoader> loaders = new ArrayList<URLClassLoader>();
    private Map<String, String> env;

    @Before
    public void requireRemoteMysql() throws Exception {
        Assume.assumeTrue(
                "SF_REMOTE_MYSQL is not enabled",
                "true".equalsIgnoreCase(System.getenv("SF_REMOTE_MYSQL")));
        String path = System.getenv("SF_COMPAT_ENV_FILE");
        if (path == null || path.length() == 0) {
            Assert.fail("SF_REMOTE_MYSQL is enabled but SF_COMPAT_ENV_FILE is missing");
        }
        env = DatabaseFixtureSupport.readEnv(new File(path));
        require("SF_COMPAT_MYSQL_HOST");
        require("SF_COMPAT_MYSQL_PORT");
        require("SF_COMPAT_MYSQL_USER");
        require("SF_COMPAT_MYSQL_PASSWORD");
        require("SF_COMPAT_MYSQL_DATABASE");
        if (!DATABASE.equals(env.get("SF_COMPAT_MYSQL_DATABASE"))) {
            Assert.fail("refusing a MySQL database other than " + DATABASE);
        }
        if (!USER.equals(env.get("SF_COMPAT_MYSQL_USER"))) {
            Assert.fail("refusing a MySQL account other than " + USER);
        }
        String host = env.get("SF_COMPAT_MYSQL_HOST");
        if (!"192.168.110.116".equals(host) && !"127.0.0.1".equals(host)) {
            Assert.fail("refusing a MySQL host other than RemoteService or its local forward");
        }
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
                execute("DROP TABLE IF EXISTS `" + DatabaseFixtureSupport.TABLE + "`");
            } catch (Exception ignored) {
                // Schema cleanup must not hide the assertion.
            }
        }
    }

    @Test
    public void httpSaveIsVisibleInRemoteMysql() throws Exception {
        execute("DROP TABLE IF EXISTS `" + DatabaseFixtureSupport.TABLE + "`");
        execute("CREATE TABLE `" + DatabaseFixtureSupport.TABLE + "` ("
                + "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, label VARCHAR(128) NULL"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");
        String label = "remote-" + System.nanoTime();
        URLClassLoader loader = DatabaseFixtureSupport.isolated(loaders);
        Settings settings = DatabaseFixtureSupport.databaseSettings(
                        env, "remote-mysql", PACKAGE, PACKAGE, "", true, false)
                .put("http.disable", "false")
                .put("http.port", "0")
                .put("test.datasources.mysql.jdbc.useSSL", "false")
                .build();
        ApplicationContext context = Bootstrap.configureSystem(
                settings,
                DatabaseFixtureSupport.marker(loader, PACKAGE + ".ServiceFrameworkPackageAnchor"));
        contexts.add(context);
        DatabaseFixtureSupport.HttpResult result = DatabaseFixtureSupport.http(
                context.httpPort(),
                "/db/orm?q=" + DatabaseFixtureSupport.url(label));
        Assert.assertEquals(result.body, 200, result.status);
        Assert.assertEquals(label + ":remote-mysql", result.body);
        String stored = queryString("SELECT label FROM `" + DatabaseFixtureSupport.TABLE + "`");
        Assert.assertEquals(label, stored);
        DatabaseFixtureSupport.closeWhenIdle(context);
    }

    private void require(String key) {
        String value = env.get(key);
        if (value == null || value.length() == 0) {
            Assert.fail("remote MySQL env is missing " + key);
        }
    }

    private void execute(String sql) throws Exception {
        Connection connection = open();
        try {
            Statement statement = connection.createStatement();
            try {
                statement.execute(sql);
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    private String queryString(String sql) throws Exception {
        Connection connection = open();
        try {
            Statement statement = connection.createStatement();
            try {
                ResultSet rows = statement.executeQuery(sql);
                try {
                    Assert.assertTrue("no row in " + DATABASE, rows.next());
                    return rows.getString(1);
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

    private Connection open() throws Exception {
        Class.forName("com.mysql.jdbc.Driver");
        String url = "jdbc:mysql://" + env.get("SF_COMPAT_MYSQL_HOST") + ":" + env.get("SF_COMPAT_MYSQL_PORT")
                + "/" + env.get("SF_COMPAT_MYSQL_DATABASE")
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&connectTimeout=5000";
        return DriverManager.getConnection(
                url,
                env.get("SF_COMPAT_MYSQL_USER"),
                env.get("SF_COMPAT_MYSQL_PASSWORD"));
    }
}
