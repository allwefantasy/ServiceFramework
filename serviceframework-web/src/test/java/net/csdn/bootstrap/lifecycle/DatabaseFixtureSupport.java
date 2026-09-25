package net.csdn.bootstrap.lifecycle;

import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import net.csdn.bootstrap.ApplicationContext;
import net.csdn.common.settings.ImmutableSettings;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Package-visible loader, settings, HTTP and DDL helpers shared by
 * {@link ApplicationDatabaseTest} and the phase benchmark. Not a production API.
 */
final class DatabaseFixtureSupport {
    static final String TABLE = "sf_web_lifecycle_record";
    static final String COLLECTION = "sf_web_lifecycle";

    private DatabaseFixtureSupport() {
    }

    static boolean webDbRequested() {
        if (Boolean.parseBoolean(System.getProperty("sf.compat.web.db", "false"))) {
            return true;
        }
        return "true".equalsIgnoreCase(System.getenv("SF_COMPAT_WEB_DB"));
    }

    static Map<String, String> readEnv(File file) throws Exception {
        Map<String, String> values = new LinkedHashMap<String, String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                int split = line.indexOf('=');
                if (split <= 0) {
                    continue;
                }
                values.put(line.substring(0, split), line.substring(split + 1));
            }
        } finally {
            reader.close();
        }
        return values;
    }

    static File configDir() {
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

    static ImmutableSettings.Builder databaseSettings(
            Map<String, String> env,
            String token,
            String controller,
            String model,
            String document,
            boolean mysql,
            boolean mongo) {
        ImmutableSettings.Builder builder = ImmutableSettings.settingsBuilder()
                .put("mode", "test")
                .put("path.conf", configDir().getAbsolutePath())
                .put("path.logs", new File("target/logs").getAbsolutePath())
                .put("cluster.name", "sf-web-lifecycle")
                .put("application.token", token)
                .put("http.host", "127.0.0.1")
                .put("http.threads.min", "8")
                .put("http.threads.max", "64")
                .put("http.disable", "true")
                .put("thrift.disable", "true")
                .put("dubbo.disable", "true")
                .put("test.datasources.redis.disable", "true")
                .put("application.template.engine.enable", "false")
                .put("application.api.qps.enable", "false")
                .put("application.log.enable", "false")
                .put("application.controller", controller)
                .put("application.controller.default", "")
                .put("application.controllerNames", "")
                .put("application.service", "")
                .put("application.util", "")
                .put("application.model", model)
                .put("application.document", document)
                .put("qpslimit.enable", "false")
                .put("test.datasources.mysql.disable", mysql ? "false" : "true")
                .put("test.datasources.mongodb.disable", mongo ? "false" : "true");
        if (mysql) {
            builder.put("test.datasources.mysql.host", env.get("SF_COMPAT_MYSQL_HOST"))
                    .put("test.datasources.mysql.port", env.get("SF_COMPAT_MYSQL_PORT"))
                    .put("test.datasources.mysql.database", env.get("SF_COMPAT_MYSQL_DATABASE"))
                    .put("test.datasources.mysql.username", env.get("SF_COMPAT_MYSQL_USER"))
                    .put("test.datasources.mysql.password", env.get("SF_COMPAT_MYSQL_PASSWORD"))
                    .put("test.datasources.mysql.show_sql", "false")
                    .put("test.datasources.mysql.driver", "com.mysql.jdbc.Driver")
                    .put("test.datasources.mysql.initialSize", "1")
                    .put("test.datasources.mysql.minIdle", "0")
                    .put("test.datasources.mysql.maxActive", "2")
                    .put("test.datasources.mysql.jdbc.connectTimeout", "5000");
        }
        if (mongo) {
            builder.put("test.datasources.mongodb.host", env.get("SF_COMPAT_MONGO_HOST"))
                    .put("test.datasources.mongodb.port", env.get("SF_COMPAT_MONGO_PORT"))
                    .put("test.datasources.mongodb.database", env.get("SF_COMPAT_MONGO_DATABASE"))
                    .put("test.datasources.mongodb.username", env.get("SF_COMPAT_MONGO_USER"))
                    .put("test.datasources.mongodb.password", env.get("SF_COMPAT_MONGO_PASSWORD"))
                    .put("test.datasources.mongodb.authenticationDatabase", env.get("SF_COMPAT_MONGO_AUTH_DB"))
                    .put("test.datasources.mongodb.replicaSet", env.get("SF_COMPAT_MONGO_REPLSET"));
        }
        return builder;
    }

    static ImmutableSettings.Builder loaderSettings(
            Map<String, String> env,
            String token,
            String model,
            String document,
            boolean mysql,
            boolean mongo) {
        ImmutableSettings.Builder builder = ImmutableSettings.settingsBuilder()
                .put("mode", "development")
                .put("path.conf", configDir().getAbsolutePath())
                .put("path.logs", new File("target/logs").getAbsolutePath())
                .put("cluster.name", "sf-web-lifecycle")
                .put("application.token", token)
                .put("application.model", model)
                .put("application.document", document)
                .put("development.datasources.mysql.disable", mysql ? "false" : "true")
                .put("development.datasources.mongodb.disable", mongo ? "false" : "true");
        if (mysql) {
            builder.put("development.datasources.mysql.host", env.get("SF_COMPAT_MYSQL_HOST"))
                    .put("development.datasources.mysql.port", env.get("SF_COMPAT_MYSQL_PORT"))
                    .put("development.datasources.mysql.database", env.get("SF_COMPAT_MYSQL_DATABASE"))
                    .put("development.datasources.mysql.username", env.get("SF_COMPAT_MYSQL_USER"))
                    .put("development.datasources.mysql.password", env.get("SF_COMPAT_MYSQL_PASSWORD"))
                    .put("development.datasources.mysql.show_sql", "false")
                    .put("development.datasources.mysql.driver", "com.mysql.jdbc.Driver")
                    .put("development.datasources.mysql.initialSize", "1")
                    .put("development.datasources.mysql.minIdle", "0")
                    .put("development.datasources.mysql.maxActive", "2")
                    .put("development.datasources.mysql.jdbc.connectTimeout", "5000");
        }
        if (mongo) {
            builder.put("development.datasources.mongodb.host", env.get("SF_COMPAT_MONGO_HOST"))
                    .put("development.datasources.mongodb.port", env.get("SF_COMPAT_MONGO_PORT"))
                    .put("development.datasources.mongodb.database", env.get("SF_COMPAT_MONGO_DATABASE"))
                    .put("development.datasources.mongodb.username", env.get("SF_COMPAT_MONGO_USER"))
                    .put("development.datasources.mongodb.password", env.get("SF_COMPAT_MONGO_PASSWORD"))
                    .put("development.datasources.mongodb.authenticationDatabase", env.get("SF_COMPAT_MONGO_AUTH_DB"))
                    .put("development.datasources.mongodb.replicaSet", env.get("SF_COMPAT_MONGO_REPLSET"));
        }
        return builder;
    }

    static void createTable(Map<String, String> env) throws Exception {
        dropTable(env);
        Class.forName("com.mysql.jdbc.Driver");
        Connection connection = openMysql(env);
        try {
            Statement statement = connection.createStatement();
            try {
                statement.execute("CREATE TABLE `" + TABLE + "` ("
                        + "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, label VARCHAR(128) NULL"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8");
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    static void dropTable(Map<String, String> env) throws Exception {
        Class.forName("com.mysql.jdbc.Driver");
        Connection connection = openMysql(env);
        try {
            Statement statement = connection.createStatement();
            try {
                statement.execute("DROP TABLE IF EXISTS `" + TABLE + "`");
            } finally {
                statement.close();
            }
        } finally {
            connection.close();
        }
    }

    static Connection openMysql(Map<String, String> env) throws Exception {
        String url = "jdbc:mysql://" + env.get("SF_COMPAT_MYSQL_HOST") + ":" + env.get("SF_COMPAT_MYSQL_PORT")
                + "/" + env.get("SF_COMPAT_MYSQL_DATABASE")
                + "?useUnicode=true&characterEncoding=utf8&connectTimeout=5000";
        return DriverManager.getConnection(
                url,
                env.get("SF_COMPAT_MYSQL_USER"),
                env.get("SF_COMPAT_MYSQL_PASSWORD"));
    }

    static void dropCollection(Map<String, String> env) throws Exception {
        MongoClientOptions.Builder options = MongoClientOptions.builder()
                .serverSelectionTimeout(8000)
                .connectTimeout(8000)
                .socketTimeout(20000);
        String replica = env.get("SF_COMPAT_MONGO_REPLSET");
        if (replica != null && replica.length() > 0) {
            options.requiredReplicaSetName(replica);
        }
        MongoCredential credential = MongoCredential.createScramSha256Credential(
                env.get("SF_COMPAT_MONGO_USER"),
                env.get("SF_COMPAT_MONGO_AUTH_DB"),
                env.get("SF_COMPAT_MONGO_PASSWORD").toCharArray());
        MongoClient client = new MongoClient(
                new ServerAddress(env.get("SF_COMPAT_MONGO_HOST"), Integer.parseInt(env.get("SF_COMPAT_MONGO_PORT"))),
                Collections.singletonList(credential),
                options.build());
        try {
            client.getDB(env.get("SF_COMPAT_MONGO_DATABASE")).getCollection(COLLECTION).drop();
        } finally {
            client.close();
        }
    }

    static void closeWhenIdle(ApplicationContext context) {
        long deadline = System.currentTimeMillis() + 4000;
        RuntimeException last = null;
        while (true) {
            try {
                context.close();
                return;
            } catch (RuntimeException thrown) {
                String message = thrown.getMessage();
                if (message == null || !message.contains("active scopes") || System.currentTimeMillis() >= deadline) {
                    last = thrown;
                    break;
                }
                last = thrown;
                try {
                    Thread.sleep(50);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw thrown;
                }
            }
        }
        if (last != null) {
            throw last;
        }
    }

    static Class<?> marker(URLClassLoader loader, String name) throws Exception {
        return Class.forName(name, false, loader);
    }

    static URLClassLoader isolated(List<URLClassLoader> tracked) throws Exception {
        URL url = DatabaseFixtureSupport.class.getProtectionDomain().getCodeSource().getLocation();
        IsolatedLoader loader = new IsolatedLoader(new URL[]{url});
        if (tracked != null) {
            tracked.add(loader);
        }
        return loader;
    }

    static String url(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8");
    }

    static HttpResult http(int port, String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(10000);
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

    static final class IsolatedLoader extends URLClassLoader {
        private IsolatedLoader(URL[] urls) {
            super(urls, DatabaseFixtureSupport.class.getClassLoader());
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

    static final class HttpResult {
        final int status;
        final String body;

        private HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
