package net.csdn.jpa;

import io.getquill.MysqlJdbcContext;
import io.getquill.PostgresJdbcContext;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.JdbcEngine;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.context.JPAConfig;
import net.csdn.jpa.model.JPABase;
import net.csdn.jpa.model.Model;
import net.csdn.jpa.type.DBInfo;
import net.csdn.jpa.type.impl.PostgresType;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.persistence.EntityManager;
import javax.sql.DataSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Real PostgreSQL path. Enabled by {@code SF_ORM_PG=true} or
 * {@code -Dsf.orm.pg=true}; the switch requires {@code SF_COMPAT_PG_ENV_FILE}.
 */
public class OrmPostgresBusinessTest {
    private static final String PRIMARY_SCHEMA = "sfa";
    private static final String REPORT_SCHEMA = "sfb";
    private static final String MIXED_SCHEMA = "SfvMixed";
    private static final String[] TABLES = new String[]{
            "sf_orm_bcr_customer",
            "sf_orm_bcr_shop_order",
            "sf_orm_bcr_bill_order",
            "sf_orm_bcr_photo",
            "sf_orm_pg_types",
            "sf_orm_bcr_meta",
            "order",
            "CamelCase"
    };

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private final List<EnhancementContext> contexts = new ArrayList<EnhancementContext>();
    private Map<String, String> env;

    @Before
    public void requireService() {
        Assume.assumeTrue(
                "sf.orm.pg is not enabled; a skip is not acceptance",
                postgresRequested());
        String path = System.getenv("SF_COMPAT_PG_ENV_FILE");
        if (path == null || path.length() == 0) {
            fail("sf.orm.pg is enabled but SF_COMPAT_PG_ENV_FILE is missing");
        }
        env = readEnv(new File(path));
        for (String key : new String[]{
                "SF_COMPAT_PG_HOST", "SF_COMPAT_PG_PORT", "SF_COMPAT_PG_USER",
                "SF_COMPAT_PG_PASSWORD", "SF_COMPAT_PG_DATABASE"}) {
            if (env.get(key) == null || env.get(key).length() == 0) {
                fail("compat env is missing " + key);
            }
        }
        if (!"sf_compat".equals(env.get("SF_COMPAT_PG_DATABASE"))) {
            fail("refusing to use a database other than sf_compat");
        }
    }

    @After
    public void cleanup() {
        try {
            QuillDB.close();
        } catch (RuntimeException ignored) {
            // Assertions are the result that matters.
        }
        for (int i = contexts.size() - 1; i >= 0; i--) {
            try {
                contexts.get(i).close();
            } catch (RuntimeException ignored) {
                // Assertions are the result that matters.
            }
        }
        contexts.clear();
        JPA.shutdown();
        if (env != null) {
            try {
                dropSchemas();
            } catch (Exception ignored) {
                // Drop is best-effort; the assertions above are the result.
            }
        }
    }

    @Test
    public void postgresPrimaryRunsModelsTypesTransactionsAndNamedPools() throws Exception {
        createSchemasAndTables();
        File classes = compileFixtures();
        URLClassLoader loader = new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader());
        Class<?> marker = Class.forName("net.csdn.jpa.ormfixture.shop.ServiceFrameworkPackageAnchor", false, loader);
        JPA.CSDNORMConfiguration configuration = new JPA.CSDNORMConfiguration(
                "development", postgresSettings("net.csdn.jpa.ormfixture"), marker);
        JPA.configure(configuration);

        assertTrue(OrmSession.current().postgresAvailable());
        assertFalse(OrmSession.current().mysqlAvailable());
        assertTrue(JPA.dbType() instanceof PostgresType);
        try {
            OrmSession.current().mysqlClient();
            fail("mysql client accepted a PostgreSQL primary");
        } catch (EnhancementFailure expected) {
            assertTrue(expected.getMessage().contains("datasources.primary=mysql"));
        }

        Class<?> customerType = Class.forName("net.csdn.jpa.ormfixture.shop.Customer", true, loader);
        Class<?> orderType = Class.forName("net.csdn.jpa.ormfixture.shop.Order", true, loader);
        Class<?> typesType = Class.forName("net.csdn.jpa.ormfixture.types.TypesRecord", true, loader);

        Object customer = customerType.getDeclaredConstructor().newInstance();
        customerType.getMethod("setName", String.class).invoke(customer, "pg-customer");
        assertTrue(((JPABase) customer).save());
        Object customerId = customerType.getMethod("getId").invoke(customer);
        assertNotNull(customerId);

        for (int i = 0; i < 3; i++) {
            Object order = orderType.getDeclaredConstructor().newInstance();
            orderType.getMethod("setStatus", String.class).invoke(order, "open");
            orderType.getMethod("setRegion", String.class).invoke(order, "pg");
            orderType.getMethod("setLabel", String.class).invoke(order, "pg-" + i);
            orderType.getMethod("setCustomer", customerType).invoke(order, customer);
            assertTrue(((JPABase) order).save());
        }
        Class<?> queries = Class.forName("net.csdn.jpa.ormfixture.shop.OrderQueries", true, loader);
        @SuppressWarnings("unchecked")
        List<Object> page = (List<Object>) queries
                .getMethod("findByStatusAndRegion", String.class, String.class, int.class, int.class)
                .invoke(null, "open", "pg", 1, 1);
        assertEquals(1, page.size());
        assertEquals("pg-1", page.get(0).getClass().getMethod("getLabel").invoke(page.get(0)));
        assertEquals(customerId, page.get(0).getClass().getMethod("getCustomer").invoke(page.get(0))
                .getClass().getMethod("getId").invoke(page.get(0).getClass().getMethod("getCustomer").invoke(page.get(0))));

        UUID token = UUID.randomUUID();
        byte[] payload = new byte[]{0, 1, 2, (byte) 0xff};
        java.util.Date happenedAt = new java.util.Date(1700000000000L);
        java.util.Date day = java.sql.Date.valueOf("2024-01-02");
        BigDecimal amount = new BigDecimal("1234.56");
        Object typed = typesType.getDeclaredConstructor().newInstance();
        typesType.getMethod("setBigValue", Long.class).invoke(typed, 9223372036854775800L);
        typesType.getMethod("setFlag", Boolean.class).invoke(typed, Boolean.TRUE);
        typesType.getMethod("setAmount", BigDecimal.class).invoke(typed, amount);
        typesType.getMethod("setHappenedAt", java.util.Date.class).invoke(typed, happenedAt);
        typesType.getMethod("setDay", java.util.Date.class).invoke(typed, day);
        typesType.getMethod("setPayload", byte[].class).invoke(typed, payload);
        typesType.getMethod("setToken", UUID.class).invoke(typed, token);
        typesType.getMethod("setLabel", String.class).invoke(typed, "pg-types");
        assertTrue(((JPABase) typed).save());
        Object typedId = typesType.getMethod("getId").invoke(typed);
        JPA.getJPAConfig().getJPAContext().closeTx(false);

        Object loadedTypes = typesType.getMethod("findById", Object.class).invoke(null, typedId);
        assertEquals(9223372036854775800L, typesType.getMethod("getBigValue").invoke(loadedTypes));
        assertEquals(Boolean.TRUE, typesType.getMethod("getFlag").invoke(loadedTypes));
        assertEquals(amount, typesType.getMethod("getAmount").invoke(loadedTypes));
        assertArrayEquals(payload, (byte[]) typesType.getMethod("getPayload").invoke(loadedTypes));
        assertEquals(token, typesType.getMethod("getToken").invoke(loadedTypes));

        Object rolled = orderType.getDeclaredConstructor().newInstance();
        orderType.getMethod("setStatus", String.class).invoke(rolled, "rolled");
        orderType.getMethod("setRegion", String.class).invoke(rolled, "pg");
        orderType.getMethod("setLabel", String.class).invoke(rolled, "rolled");
        assertTrue(((JPABase) rolled).save());
        Object rolledId = orderType.getMethod("getId").invoke(rolled);
        JPA.getJPAConfig().getJPAContext().closeTx(true);
        assertNull(orderType.getMethod("findById", Object.class).invoke(null, rolledId));

        List<Map> current = Model.findBySql("SELECT current_schema() AS schema_name, pg_backend_pid() AS pid");
        assertEquals(PRIMARY_SCHEMA, String.valueOf(current.get(0).get("schema_name")));
        assertTrue(((Number) current.get(0).get("pid")).longValue() > 0);
        assertNotNull(Model.nativeSqlClient("report"));
        List<Map> named = Model.nativeSqlClient("report").query("SELECT note FROM \"order\" WHERE id=1");
        assertEquals("sfb", named.get(0).get("note"));

        PostgresJdbcContext postgresCtx = QuillDB.postgresCtx();
        assertTrue(String.valueOf(postgresCtx.probe("SELECT 1")), postgresCtx.probe("SELECT 1").isSuccess());
        try {
            QuillDB.ctx();
            fail("MySQL Quill API accepted a PostgreSQL primary");
        } catch (EnhancementFailure expected) {
            assertTrue(expected.getMessage().contains("datasources.primary=mysql"));
        }

        JPAConfig config = JPA.getJPAConfig();
        JPA.shutdown();
        assertFalse(config.isEnabled());
    }

    @Test
    public void ormSavesReadsAndSeesCurrentSchemaInMixedCaseSchema() throws Exception {
        createSchemasAndTables();
        File classes = compileFixtures();
        URLClassLoader loader = new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader());
        Class<?> marker = Class.forName("net.csdn.jpa.ormfixture.shop.ServiceFrameworkPackageAnchor", false, loader);
        JPA.CSDNORMConfiguration configuration = new JPA.CSDNORMConfiguration(
                "development", postgresSettings("net.csdn.jpa.ormfixture", MIXED_SCHEMA), marker);
        JPA.configure(configuration);

        Class<?> customerType = Class.forName("net.csdn.jpa.ormfixture.shop.Customer", true, loader);
        Object customer = customerType.getDeclaredConstructor().newInstance();
        customerType.getMethod("setName", String.class).invoke(customer, "mixed-case");
        assertTrue(((JPABase) customer).save());
        Object customerId = customerType.getMethod("getId").invoke(customer);
        assertNotNull(customerId);
        JPA.getJPAConfig().getJPAContext().closeTx(false);

        Object loaded = customerType.getMethod("findById", Object.class).invoke(null, customerId);
        assertNotNull(loaded);
        assertEquals("mixed-case", loaded.getClass().getMethod("getName").invoke(loaded));
        List<Map> current = Model.findBySql("SELECT current_schema() AS schema_name");
        assertEquals(MIXED_SCHEMA, String.valueOf(current.get(0).get("schema_name")));
        assertTrue(JPA.dbInfo().identity().endsWith("#schema=" + MIXED_SCHEMA));
        JPA.shutdown();
    }

    @Test
    public void quotedSchemaConfigAndExplicitCurrentSchemaReachSameMixedCaseSchema() throws Exception {
        createSchemasAndTables();
        File classes = compileFixtures();
        URLClassLoader loader = new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader());
        Class<?> marker = Class.forName("net.csdn.jpa.ormfixture.shop.ServiceFrameworkPackageAnchor", false, loader);
        Settings settings = postgresSettings(
                "net.csdn.jpa.ormfixture", "\"" + MIXED_SCHEMA + "\"", MIXED_SCHEMA);
        JPA.configure(new JPA.CSDNORMConfiguration("development", settings, marker));

        Class<?> customerType = Class.forName("net.csdn.jpa.ormfixture.shop.Customer", true, loader);
        Object customer = customerType.getDeclaredConstructor().newInstance();
        customerType.getMethod("setName", String.class).invoke(customer, "quoted-config");
        assertTrue(((JPABase) customer).save());
        JPA.getJPAConfig().getJPAContext().closeTx(false);

        Object loaded = customerType.getMethod("findById", Object.class)
                .invoke(null, customerType.getMethod("getId").invoke(customer));
        assertNotNull(loaded);
        List<Map> current = Model.findBySql("SELECT current_schema() AS schema_name");
        assertEquals(MIXED_SCHEMA, String.valueOf(current.get(0).get("schema_name")));
        JPA.shutdown();
    }

    @Test
    public void wrongEngineNamedQuillLookupIsRejected() throws Exception {
        Assume.assumeTrue(
                "sf.orm.mysql is not enabled; a mixed-engine lane needs both databases",
                mysqlRequested());
        Map<String, String> mysql = mysqlEnv();
        createSchemasAndTables();
        File classes = compileFixtures();
        URLClassLoader loader = new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader());
        Class<?> marker = Class.forName("net.csdn.jpa.ormfixture.shop.ServiceFrameworkPackageAnchor", false, loader);
        JPA.CSDNORMConfiguration configuration = new JPA.CSDNORMConfiguration(
                "development", mixedEngineSettings("net.csdn.jpa.ormfixture", mysql), marker);
        JPA.configure(configuration);

        PostgresJdbcContext report = QuillDB.createNewPostgresCtxByNameFromYml("report");
        assertTrue(String.valueOf(report.probe("SELECT 1")), report.probe("SELECT 1").isSuccess());
        MysqlJdbcContext mymysql = QuillDB.createNewCtxByNameFromYml("mymysql");
        assertTrue(String.valueOf(mymysql.probe("SELECT 1")), mymysql.probe("SELECT 1").isSuccess());

        expectEngineMismatch(JdbcEngine.MYSQL, "report", JdbcEngine.POSTGRES);
        expectEngineMismatch(JdbcEngine.POSTGRES, "mymysql", JdbcEngine.MYSQL);

        assertSame(report, QuillDB.createNewPostgresCtxByNameFromYml("report"));
        assertSame(mymysql, QuillDB.createNewCtxByNameFromYml("mymysql"));
        List<Map> reportVersion = Model.nativeSqlClient("report").query("SELECT version() AS v");
        assertTrue(String.valueOf(reportVersion.get(0).get("v")).contains("PostgreSQL"));
        List<Map> mysqlVersion = Model.nativeSqlClient("mymysql").query("SELECT version() AS v");
        assertFalse(String.valueOf(mysqlVersion.get(0).get("v")).contains("PostgreSQL"));
        JPA.shutdown();
    }

    private void expectEngineMismatch(String engine, String name, String actual) {
        try {
            if (JdbcEngine.MYSQL.equals(engine)) {
                QuillDB.createNewCtxByNameFromYml(name);
            } else {
                QuillDB.createNewPostgresCtxByNameFromYml(name);
            }
            fail(engine + "-typed Quill lookup accepted a " + actual + " pool: " + name);
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
            assertTrue(failure.getMessage(), failure.getMessage().contains("expected " + engine));
            assertTrue(failure.getMessage(), failure.getMessage().contains("actual " + actual));
            assertTrue(failure.getMessage(), failure.getMessage().contains(name));
        }
    }

    @Test
    public void metadataIsScopedToSelectedSchemaAndRefreshes() throws Exception {
        createSchemasAndTables();
        EnhancementContext context = track(EnhancementContext.open(JPA.class.getClassLoader()));
        try (EnhancementContext.Scope ignored = context.activate()) {
            Settings settings = postgresSettings("net.csdn.jpa.dbinfo");
            JPA.CSDNORMConfiguration configuration = new JPA.CSDNORMConfiguration("development", settings, JPA.class);
            OrmSession.attach(context).bindConfiguration(configuration);
            DBInfo info = new DBInfo(settings);
            Map<String, String> order = info.columns("order");
            assertNotNull(order);
            assertEquals("VARCHAR", order.get("marker"));
            assertFalse(order.containsKey("other"));
            Map<String, String> camel = info.columns("CamelCase");
            assertNotNull(camel);
            assertEquals("VARCHAR", camel.get("DisplayName"));
            assertTrue(info.hasTable("camelcase"));
            for (String table : info.tableNames()) {
                assertFalse(table, table.startsWith("pg_"));
                assertFalse(table, table.startsWith("sql_"));
                assertFalse(table, "information_schema".equals(table));
            }
            String digest = info.schemaDigest();
            assertTrue(info.identity().endsWith("#schema=" + PRIMARY_SCHEMA));
            try (Connection connection = open(); Statement statement = connection.createStatement()) {
                statement.execute("ALTER TABLE " + PRIMARY_SCHEMA + ".\"CamelCase\" ADD COLUMN fresh_note VARCHAR(20)");
            }
            info.refresh();
            assertEquals("VARCHAR", info.columns("camelcase").get("fresh_note"));
            assertFalse(digest.equals(info.schemaDigest()));
            assertEquals("\"Mi\"\"xed\"", DBInfo.quoteIdentifier(JdbcEngine.POSTGRES, "Mi\"xed"));
        }
    }

    @Test
    public void twoPostgresContextsKeepPoolsAndCloseIndependently() throws Exception {
        createSchemasAndTables();
        File classes = compileFixtures();
        EnhancementContext left = track(EnhancementContext.open(
                new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader())));
        EnhancementContext right = track(EnhancementContext.open(
                new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader())));
        Settings settings = postgresSettings("net.csdn.jpa.ormfixture");

        DataSource leftPool;
        long leftPid;
        Class<?> leftMarker = Class.forName("net.csdn.jpa.ormfixture.shop.ServiceFrameworkPackageAnchor", false, left.targetLoader());
        JPA.configure(new JPA.CSDNORMConfiguration("development", settings, leftMarker), left);
        try (EnhancementContext.Scope ignored = left.activate()) {
            Class<?> customerType = Class.forName("net.csdn.jpa.ormfixture.shop.Customer", true, left.targetLoader());
            Object customer = customerType.getDeclaredConstructor().newInstance();
            customerType.getMethod("setName", String.class).invoke(customer, "left-pg");
            assertTrue(((JPABase) customer).save());
            JPA.getJPAConfig().getJPAContext().closeTx(false);
            leftPool = OrmSession.current().postgresClient().defaultMysqlService().dataSource();
            leftPid = pid(Model.nativeSqlClient().query("SELECT pg_backend_pid() AS pid"));
            assertTrue(String.valueOf(QuillDB.postgresCtx().probe("SELECT 1")), QuillDB.postgresCtx().probe("SELECT 1").isSuccess());
        }

        Class<?> rightMarker = Class.forName("net.csdn.jpa.ormfixture.shop.ServiceFrameworkPackageAnchor", false, right.targetLoader());
        JPA.configure(new JPA.CSDNORMConfiguration("development", settings, rightMarker), right);
        DataSource rightPool;
        long rightPid;
        try (EnhancementContext.Scope ignored = right.activate()) {
            Class<?> customerType = Class.forName("net.csdn.jpa.ormfixture.shop.Customer", true, right.targetLoader());
            Object customer = customerType.getDeclaredConstructor().newInstance();
            customerType.getMethod("setName", String.class).invoke(customer, "right-pg");
            assertTrue(((JPABase) customer).save());
            JPA.getJPAConfig().getJPAContext().closeTx(false);
            rightPool = OrmSession.current().postgresClient().defaultMysqlService().dataSource();
            rightPid = pid(Model.nativeSqlClient().query("SELECT pg_backend_pid() AS pid"));
            EntityManager manager = JPA.getJPAConfig().getJPAContext().em();
            assertTrue(singleNumber(manager.createNativeQuery("SELECT pg_backend_pid()").getSingleResult()) > 0);
        }

        assertNotSame(leftPool, rightPool);
        assertNotEquals(leftPid, rightPid);
        left.close();
        try (EnhancementContext.Scope ignored = right.activate()) {
            Class<?> customerType = Class.forName("net.csdn.jpa.ormfixture.shop.Customer", true, right.targetLoader());
            Object customer = customerType.getDeclaredConstructor().newInstance();
            customerType.getMethod("setName", String.class).invoke(customer, "right-after-left");
            assertTrue(((JPABase) customer).save());
            JPA.getJPAConfig().getJPAContext().closeTx(false);
        }
    }

    private EnhancementContext track(EnhancementContext context) {
        contexts.add(context);
        return context;
    }

    private Settings postgresSettings(String modelPackage) {
        return postgresSettings(modelPackage, PRIMARY_SCHEMA);
    }

    private Settings postgresSettings(String modelPackage, String schemaName) {
        return postgresSettings(modelPackage, schemaName, null);
    }

    private Settings postgresSettings(String modelPackage, String schemaName, String currentSchema) {
        ImmutableSettings.Builder builder = baseSettings(modelPackage)
                .put("development.datasources.postgres.schema", schemaName);
        if (currentSchema != null) {
            builder.put("development.datasources.postgres.jdbc.currentSchema", currentSchema);
        }
        return builder.build();
    }

    private ImmutableSettings.Builder baseSettings(String modelPackage) {
        return ImmutableSettings.settingsBuilder()
                .put("development.datasources.primary", "postgres")
                .put("development.datasources.mysql.disable", "true")
                .put("development.datasources.postgres.host", env.get("SF_COMPAT_PG_HOST"))
                .put("development.datasources.postgres.port", env.get("SF_COMPAT_PG_PORT"))
                .put("development.datasources.postgres.database", env.get("SF_COMPAT_PG_DATABASE"))
                .put("development.datasources.postgres.username", env.get("SF_COMPAT_PG_USER"))
                .put("development.datasources.postgres.password", env.get("SF_COMPAT_PG_PASSWORD"))
                .put("development.datasources.postgres.show_sql", "false")
                .put("development.datasources.postgres.initialSize", "1")
                .put("development.datasources.postgres.minIdle", "0")
                .put("development.datasources.postgres.maxActive", "2")
                .put("development.datasources.postgres.jdbc.connectTimeout", "5")
                .put("development.datasources.multi-postgres.report.host", env.get("SF_COMPAT_PG_HOST"))
                .put("development.datasources.multi-postgres.report.port", env.get("SF_COMPAT_PG_PORT"))
                .put("development.datasources.multi-postgres.report.database", env.get("SF_COMPAT_PG_DATABASE"))
                .put("development.datasources.multi-postgres.report.schema", REPORT_SCHEMA)
                .put("development.datasources.multi-postgres.report.username", env.get("SF_COMPAT_PG_USER"))
                .put("development.datasources.multi-postgres.report.password", env.get("SF_COMPAT_PG_PASSWORD"))
                .put("development.datasources.multi-postgres.report.show_sql", "false")
                .put("development.datasources.multi-postgres.report.initialSize", "1")
                .put("development.datasources.multi-postgres.report.minIdle", "0")
                .put("development.datasources.multi-postgres.report.maxActive", "1")
                .put("application.model", modelPackage);
    }

    private Settings mixedEngineSettings(String modelPackage, Map<String, String> mysql) {
        return baseSettings(modelPackage)
                .put("development.datasources.postgres.schema", PRIMARY_SCHEMA)
                .put("development.datasources.multi-mysql.mymysql.host", mysql.get("SF_COMPAT_MYSQL_HOST"))
                .put("development.datasources.multi-mysql.mymysql.port", mysql.get("SF_COMPAT_MYSQL_PORT"))
                .put("development.datasources.multi-mysql.mymysql.database", mysql.get("SF_COMPAT_MYSQL_DATABASE"))
                .put("development.datasources.multi-mysql.mymysql.username", mysql.get("SF_COMPAT_MYSQL_USER"))
                .put("development.datasources.multi-mysql.mymysql.password", mysql.get("SF_COMPAT_MYSQL_PASSWORD"))
                .put("development.datasources.multi-mysql.mymysql.show_sql", "false")
                .put("development.datasources.multi-mysql.mymysql.initialSize", "1")
                .put("development.datasources.multi-mysql.mymysql.minIdle", "0")
                .put("development.datasources.multi-mysql.mymysql.maxActive", "1")
                .build();
    }

    private Map<String, String> mysqlEnv() {
        String path = System.getenv("SF_COMPAT_ENV_FILE");
        if (path == null || path.length() == 0) {
            fail("sf.orm.mysql is enabled but SF_COMPAT_ENV_FILE is missing");
        }
        Map<String, String> mysql = readEnv(new File(path));
        for (String key : new String[]{
                "SF_COMPAT_MYSQL_HOST", "SF_COMPAT_MYSQL_PORT", "SF_COMPAT_MYSQL_USER",
                "SF_COMPAT_MYSQL_PASSWORD", "SF_COMPAT_MYSQL_DATABASE"}) {
            if (mysql.get(key) == null || mysql.get(key).length() == 0) {
                fail("mysql compat env is missing " + key);
            }
        }
        if (!"sf_compat".equals(mysql.get("SF_COMPAT_MYSQL_DATABASE"))) {
            fail("refusing to use a database other than sf_compat");
        }
        return mysql;
    }

    private static boolean mysqlRequested() {
        return Boolean.parseBoolean(System.getProperty("sf.orm.mysql", "false"))
                || "true".equalsIgnoreCase(System.getenv("SF_ORM_MYSQL"));
    }

    private void createSchemasAndTables() throws Exception {
        dropSchemas();
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + PRIMARY_SCHEMA);
            statement.execute("CREATE SCHEMA " + REPORT_SCHEMA);
            statement.execute("CREATE SCHEMA \"" + MIXED_SCHEMA + "\"");
            createOrmTables(statement, PRIMARY_SCHEMA);
            statement.execute("CREATE TABLE " + REPORT_SCHEMA + ".\"order\" (id INTEGER PRIMARY KEY, other VARCHAR(8), note VARCHAR(8))");
            statement.execute("INSERT INTO " + REPORT_SCHEMA + ".\"order\" VALUES (1, 'nope', 'sfb')");
            createOrmTables(statement, "\"" + MIXED_SCHEMA + "\"");
        }
    }

    private void createOrmTables(Statement statement, String schema) throws Exception {
        statement.execute("CREATE TABLE " + schema + ".\"sf_orm_bcr_customer\" (id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, name VARCHAR(64) NULL)");
        statement.execute("CREATE TABLE " + schema + ".\"sf_orm_bcr_shop_order\" (id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, status VARCHAR(32) NULL, region VARCHAR(32) NULL, label VARCHAR(64) NULL, customer_id INTEGER NULL)");
        statement.execute("CREATE TABLE " + schema + ".\"sf_orm_bcr_bill_order\" (id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, code VARCHAR(32) NULL)");
        statement.execute("CREATE TABLE " + schema + ".\"sf_orm_bcr_photo\" (id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, root_tag VARCHAR(64) NULL, middle_note VARCHAR(64) NULL, name VARCHAR(64) NULL)");
        statement.execute("CREATE TABLE " + schema + ".\"sf_orm_pg_types\" (id INTEGER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, big_value BIGINT NULL, flag BOOLEAN NULL, amount NUMERIC(12,2) NULL, happened_at TIMESTAMP NULL, day DATE NULL, payload BYTEA NULL, token UUID NULL, label VARCHAR(64) NULL)");
        statement.execute("CREATE TABLE " + schema + ".\"sf_orm_bcr_meta\" (id INTEGER PRIMARY KEY, title VARCHAR(32), qty BIGINT, active BOOLEAN, amount NUMERIC(12,2), happened_at TIMESTAMP, day DATE, payload BYTEA, token UUID)");
        statement.execute("CREATE TABLE " + schema + ".\"order\" (id INTEGER PRIMARY KEY, marker VARCHAR(8))");
        statement.execute("CREATE TABLE " + schema + ".\"CamelCase\" (id INTEGER PRIMARY KEY, \"DisplayName\" VARCHAR(32))");
    }

    private void dropSchemas() throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA IF EXISTS " + PRIMARY_SCHEMA + " CASCADE");
            statement.execute("DROP SCHEMA IF EXISTS " + REPORT_SCHEMA + " CASCADE");
            statement.execute("DROP SCHEMA IF EXISTS \"" + MIXED_SCHEMA + "\" CASCADE");
        }
    }

    private Connection open() throws Exception {
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(jdbcUrl(), env.get("SF_COMPAT_PG_USER"), env.get("SF_COMPAT_PG_PASSWORD"));
    }

    private String jdbcUrl() {
        return "jdbc:postgresql://" + env.get("SF_COMPAT_PG_HOST") + ":" + env.get("SF_COMPAT_PG_PORT")
                + "/" + env.get("SF_COMPAT_PG_DATABASE") + "?connectTimeout=5";
    }

    private File compileFixtures() throws Exception {
        File root = fixtureRoot();
        List<File> sources = new ArrayList<File>();
        collectJava(root, sources);
        File classes = folder.newFolder("classes");
        File generated = folder.newFolder("generated");
        List<String> command = new ArrayList<String>();
        command.add(javacBinary().getAbsolutePath());
        String spec = System.getProperty("java.specification.version");
        if ("1.8".equals(spec) || "8".equals(spec)) {
            command.add("-source");
            command.add("8");
            command.add("-target");
            command.add("8");
        } else {
            command.add("--release");
            command.add("8");
        }
        command.add("-encoding");
        command.add("UTF-8");
        command.add("-classpath");
        command.add(System.getProperty("java.class.path"));
        command.add("-processor");
        command.add("net.csdn.jpa.query.ServiceFrameworkQueryProcessor");
        command.add("-processorpath");
        command.add(System.getProperty("java.class.path"));
        command.add("-d");
        command.add(classes.getAbsolutePath());
        command.add("-s");
        command.add(generated.getAbsolutePath());
        for (int i = 0; i < sources.size(); i++) {
            command.add(sources.get(i).getAbsolutePath());
        }
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] output = readAll(process.getInputStream());
        int exit = process.waitFor();
        if (exit != 0) {
            fail("fixture compile failed: " + new String(output, StandardCharsets.UTF_8));
        }
        return classes;
    }

    private static File javacBinary() {
        String home = System.getProperty("java.home");
        File direct = new File(home, "bin/javac");
        if (direct.isFile()) {
            return direct;
        }
        File sibling = new File(home, "../bin/javac");
        if (sibling.isFile()) {
            return sibling;
        }
        throw new IllegalStateException("javac was not found under " + home);
    }

    private static File fixtureRoot() throws Exception {
        URL url = OrmPostgresBusinessTest.class.getResource("/net/csdn/jpa/ormfixture/shop/Order.java");
        if (url == null) {
            throw new IllegalStateException("fixture sources are not on the test classpath");
        }
        return new File(url.toURI()).getParentFile().getParentFile();
    }

    private static void collectJava(File directory, List<File> sources) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        Arrays.sort(files);
        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                collectJava(files[i], sources);
            } else if (files[i].getName().endsWith(".java")) {
                sources.add(files[i]);
            }
        }
    }

    private static long pid(List<Map> rows) {
        Object value = rows.get(0).get("pid");
        if (value == null) {
            value = rows.get(0).get("PID");
        }
        return singleNumber(value);
    }

    private static long singleNumber(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static boolean postgresRequested() {
        return Boolean.parseBoolean(System.getProperty("sf.orm.pg", "false"))
                || "true".equalsIgnoreCase(System.getenv("SF_ORM_PG"));
    }

    private static Map<String, String> readEnv(File file) {
        try {
            Map<String, String> values = new LinkedHashMap<String, String>();
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.length() == 0 || line.charAt(0) == '#') {
                    continue;
                }
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                values.put(line.substring(0, eq), line.substring(eq + 1));
            }
            return values;
        } catch (Exception e) {
            throw new IllegalStateException("compat env file could not be read", e);
        }
    }

    private static byte[] readAll(InputStream input) throws Exception {
        byte[] buffer = new byte[4096];
        int read;
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        while ((read = input.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

}

