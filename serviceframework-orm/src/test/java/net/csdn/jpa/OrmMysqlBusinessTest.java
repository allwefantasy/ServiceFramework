package net.csdn.jpa;

import javassist.CtClass;
import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementPlan;
import net.csdn.common.enhancer.EnhancementRule;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import com.alibaba.druid.pool.DruidDataSource;
import io.getquill.MysqlJdbcContext;
import net.csdn.jpa.context.JPAConfig;
import net.csdn.jpa.context.JPAContext;
import net.csdn.jpa.enhancer.ModelClass;
import net.csdn.jpa.model.JPABase;
import net.csdn.jpa.model.Model;
import net.csdn.jpa.type.DBInfo;
import net.csdn.jpa.type.impl.MysqlType;
import net.csdn.modules.persist.mysql.DataSourceManager;
import org.hibernate.Session;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.persistence.EntityManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Real MySQL round trip. A skip when {@code sf.orm.mysql} is absent is not acceptance.
 */
public class OrmMysqlBusinessTest {

    private static boolean mysqlRequested() {
        if (Boolean.parseBoolean(System.getProperty("sf.orm.mysql", "false"))) {
            return true;
        }
        return "true".equalsIgnoreCase(System.getenv("SF_ORM_MYSQL"));
    }

    private static final AtomicInteger EXTRA_RULE_HITS = new AtomicInteger();
    private static final String[] TABLES = new String[]{
            "sf_orm_bcr_customer",
            "sf_orm_bcr_shop_order",
            "sf_orm_bcr_bill_order",
            "sf_orm_bcr_photo",
            "sf_orm_bcr_meta",
            "order"
    };

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private Map<String, String> env;

    @Before
    public void requireService() {
        Assume.assumeTrue(
                "sf.orm.mysql is not enabled; a skip is not acceptance",
                mysqlRequested());
        String path = System.getenv("SF_COMPAT_ENV_FILE");
        if (path == null || path.length() == 0) {
            fail("sf.orm.mysql is enabled but SF_COMPAT_ENV_FILE is missing");
        }
        env = readEnv(new File(path));
        for (String key : new String[]{
                "SF_COMPAT_MYSQL_HOST", "SF_COMPAT_MYSQL_PORT", "SF_COMPAT_MYSQL_USER",
                "SF_COMPAT_MYSQL_PASSWORD", "SF_COMPAT_MYSQL_DATABASE"}) {
            if (env.get(key) == null || env.get(key).length() == 0) {
                fail("compat env is missing " + key);
            }
        }
        if (!"sf_compat".equals(env.get("SF_COMPAT_MYSQL_DATABASE"))) {
            fail("refusing to use a database other than sf_compat");
        }
    }

    @After
    public void cleanup() {
        try {
            QuillDB.close();
        } catch (RuntimeException ignored) {
            // The assertion below is the result that matters.
        }
        JPA.shutdown();
        if (env != null) {
            try {
                dropTables();
            } catch (Exception ignored) {
                // The test assertion is the result that matters; drop is best-effort.
            }
        }
    }

    @Test
    public void savesQueriesRollsBackAndResolvesModels() throws Exception {
        EXTRA_RULE_HITS.set(0);
        createBusinessTables();
        File classes = compileFixtures();
        URLClassLoader loader = new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader());
        Class<?> marker = Class.forName("net.csdn.jpa.ormfixture.shop.ServiceFrameworkPackageAnchor", false, loader);
        Settings settings = mysqlSettings("net.csdn.jpa.ormfixture", env.get("SF_COMPAT_MYSQL_DATABASE"));
        JPA.CSDNORMConfiguration configuration = new JPA.CSDNORMConfiguration("development", settings, marker);
        configuration.addEnhancementRule(new CountingRule());
        JPA.configure(configuration);
        JPA.configure(configuration);
        assertTrue(EXTRA_RULE_HITS.get() > 0);
        List<EnhancementPlan.Execution> executions = JPA.defaultContext().executions();
        assertEquals(EnhancementRuleIds.ENTITY_MAPPING, executions.get(0).ruleId());
        assertTrue(ruleOffset(executions, EnhancementRuleIds.ORM_QUERY) > ruleOffset(executions, EnhancementRuleIds.ENTITY_MAPPING));
        assertTrue(ruleOffset(executions, EnhancementRuleIds.ASSOCIATION) > ruleOffset(executions, EnhancementRuleIds.ENTITY_MAPPING));

        Class<?> shopOrder = Class.forName("net.csdn.jpa.ormfixture.shop.Order", true, loader);
        Class<?> billingOrder = Class.forName("net.csdn.jpa.ormfixture.billing.Order", true, loader);
        Class<?> customerType = Class.forName("net.csdn.jpa.ormfixture.shop.Customer", true, loader);
        Class<?> photoType = Class.forName("net.csdn.jpa.ormfixture.tree.PhotoAsset", true, loader);
        Class<?> taggedType = Class.forName("net.csdn.jpa.ormfixture.tree.TaggedAsset", true, loader);
        Class<?> assetType = Class.forName("net.csdn.jpa.ormfixture.tree.Asset", true, loader);
        assertEquals(taggedType, photoType.getSuperclass());
        assertEquals(assetType, taggedType.getSuperclass());
        assertEquals(Model.class, assetType.getSuperclass());
        assertEquals(shopOrder, JPA.resolveModel(shopOrder.getName()));
        assertEquals(billingOrder, JPA.resolveModel("BillingOrder"));
        assertEquals(billingOrder, JPA.resolveModel(billingOrder.getName()));
        try {
            JPA.resolveModel("Order");
            fail("shared simple name was resolved");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
            assertTrue(failure.getMessage().contains(shopOrder.getName()));
            assertTrue(failure.getMessage().contains(billingOrder.getName()));
        }
        List<Class<? extends Model>> values = new ArrayList<Class<? extends Model>>(JPA.models.values());
        assertEquals(new HashSet<Class<? extends Model>>(values).size(), values.size());
        assertTrue(values.contains(shopOrder));
        assertTrue(values.contains(billingOrder));
        assertFalse(values.contains(assetType));

        Object shop = shopOrder.getDeclaredConstructor().newInstance();
        shopOrder.getMethod("setLabel", String.class).invoke(shop, "  ab ");
        assertEquals("ab", shopOrder.getMethod("getLabel").invoke(shop));
        shopOrder.getMethod("setLabel", String.class, String.class).invoke(shop, "a", "b");
        assertEquals("ab", shopOrder.getMethod("getLabel").invoke(shop));
        java.lang.reflect.Field shopParent = shopOrder.getDeclaredField("parent$_validate_info");
        java.lang.reflect.Field photoParent = photoType.getDeclaredField("parent$_validate_info");
        shopParent.setAccessible(true);
        photoParent.setAccessible(true);
        shopOrder.getMethod("validate", String.class, Map.class).invoke(null, "label", new LinkedHashMap<String, Object>());
        assertNotNull(shopParent.get(null));
        assertNull(photoParent.get(null));
        assertNotSame(shopParent.get(null), photoParent.get(null));

        Object photo = photoType.getDeclaredConstructor().newInstance();
        photoType.getMethod("setRootTag", String.class).invoke(photo, "  root ");
        assertEquals("root", photoType.getMethod("getRootTag").invoke(photo));
        photoType.getMethod("setRootTag", String.class, String.class).invoke(photo, "p", "q");
        assertEquals("pq", photoType.getMethod("getRootTag").invoke(photo));
        photoType.getMethod("setMiddleNote", String.class).invoke(photo, "mid");
        photoType.getMethod("setName", String.class).invoke(photo, "  pic ");
        assertEquals("pic", photoType.getMethod("getName").invoke(photo));
        photoType.getMethod("setName", String.class, String.class).invoke(photo, "n", "s");
        assertEquals("n:s", photoType.getMethod("getName").invoke(photo));
        assertTrue(((JPABase) photo).save());
        Object photoId = photoType.getMethod("getId").invoke(photo);

        Object customer = customerType.getDeclaredConstructor().newInstance();
        customerType.getMethod("setName", String.class).invoke(customer, "ada");
        assertTrue(((JPABase) customer).save());
        Object customerId = customerType.getMethod("getId").invoke(customer);

        String[] labels = new String[]{"a", "b", "c"};
        Object secondId = null;
        for (int i = 0; i < labels.length; i++) {
            Object order = shopOrder.getDeclaredConstructor().newInstance();
            shopOrder.getMethod("setStatus", String.class).invoke(order, "open");
            shopOrder.getMethod("setRegion", String.class).invoke(order, "east");
            shopOrder.getMethod("setLabel", String.class).invoke(order, labels[i]);
            assertTrue(((JPABase) order).save());
            if (i == 1) {
                secondId = shopOrder.getMethod("getId").invoke(order);
            }
        }
        Class<?> queries = Class.forName("net.csdn.jpa.ormfixture.shop.OrderQueries", true, loader);
        Method query = queries.getMethod("findByStatusAndRegion", String.class, String.class, int.class, int.class);
        @SuppressWarnings("unchecked")
        List<Object> page = (List<Object>) query.invoke(null, "open", "east", 1, 1);
        assertEquals(1, page.size());
        assertEquals(secondId, page.get(0).getClass().getMethod("getId").invoke(page.get(0)));
        assertEquals("b", page.get(0).getClass().getMethod("getLabel").invoke(page.get(0)));

        Object linked = shopOrder.getDeclaredConstructor().newInstance();
        shopOrder.getMethod("setStatus", String.class).invoke(linked, "hold");
        shopOrder.getMethod("setRegion", String.class).invoke(linked, "west");
        shopOrder.getMethod("setLabel", String.class).invoke(linked, "linked");
        shopOrder.getMethod("setCustomer", customerType).invoke(linked, customer);
        assertTrue(((JPABase) linked).save());
        Object linkedId = shopOrder.getMethod("getId").invoke(linked);
        JPA.getJPAConfig().getJPAContext().closeTx(false);

        Object reloaded = shopOrder.getMethod("findById", Object.class).invoke(null, linkedId);
        assertNotNull(reloaded);
        Object reloadedCustomer = shopOrder.getMethod("getCustomer").invoke(reloaded);
        assertEquals(customerId, reloadedCustomer.getClass().getMethod("getId").invoke(reloadedCustomer));
        Object reloadedPhoto = photoType.getMethod("findById", Object.class).invoke(null, photoId);
        assertEquals("pq", photoType.getMethod("getRootTag").invoke(reloadedPhoto));
        assertEquals("mid", photoType.getMethod("getMiddleNote").invoke(reloadedPhoto));
        assertEquals("n:s", photoType.getMethod("getName").invoke(reloadedPhoto));

        Object rolled = shopOrder.getDeclaredConstructor().newInstance();
        shopOrder.getMethod("setStatus", String.class).invoke(rolled, "temp");
        shopOrder.getMethod("setRegion", String.class).invoke(rolled, "east");
        shopOrder.getMethod("setLabel", String.class).invoke(rolled, "gone");
        assertTrue(((JPABase) rolled).save());
        Object rolledId = shopOrder.getMethod("getId").invoke(rolled);
        JPA.getJPAConfig().getJPAContext().closeTx(true);
        assertNull(shopOrder.getMethod("findById", Object.class).invoke(null, rolledId));

        Object billing = billingOrder.getDeclaredConstructor().newInstance();
        billingOrder.getMethod("setCode", String.class).invoke(billing, "B-1");
        assertTrue(((JPABase) billing).save());
        JPA.getJPAConfig().getJPAContext().closeTx(false);

        Settings other = mysqlSettings("net.csdn.jpa.ormfixture", "sf_orm_bcr_other");
        try {
            JPA.configure(new JPA.CSDNORMConfiguration("development", other, marker));
            fail("a different configuration reused a loader that already defined models");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFLICT, failure.getCategory());
            assertTrue(failure.getMessage().contains("ClassLoader"));
        }
        assertNotNull(shopOrder.getMethod("findById", Object.class).invoke(null, linkedId));

        JPAConfig config = JPA.getJPAConfig();
        assertTrue(config.isEnabled());
        JPA.shutdown();
        assertFalse(config.isEnabled());
        assertFalse(JPA.isConfigured());
    }

    @Test
    public void refreshesSchemaMetadataAndMeasuresTheOldProbe() throws Exception {
        createProbeTables();
        EnhancementContext context = EnhancementContext.open(JPA.class.getClassLoader());
        try {
            try (EnhancementContext.Scope ignored = context.activate()) {
                Settings settings = mysqlSettings("net.csdn.jpa.dbinfo", env.get("SF_COMPAT_MYSQL_DATABASE"));
                JPA.CSDNORMConfiguration configuration = new JPA.CSDNORMConfiguration("development", settings, JPA.class);
                OrmSession session = OrmSession.attach(context);
                session.bindConfiguration(configuration);
                DBInfo info = new DBInfo(settings);
                Map<String, String> columns = info.columns("sf_orm_bcr_meta");
                assertNotNull(columns);
                assertEquals("INT", columns.get("id"));
                assertEquals("VARCHAR", columns.get("title"));
                assertEquals("BIGINT", columns.get("qty"));
                assertEquals("DATETIME", columns.get("created_at"));
                MysqlType types = new MysqlType();
                assertEquals("Integer", types.typeToJava(columns.get("id")).v2());
                assertEquals("String", types.typeToJava(columns.get("title")).v2());
                assertEquals("Long", types.typeToJava(columns.get("qty")).v2());
                assertEquals("java.util.Date", types.typeToJava(columns.get("created_at")).v2());
                assertRawTypeMatches(columns);
                int before = info.tableNames().size();
                info.refresh();
                assertEquals(before, info.tableNames().size());
                assertEquals(1, frequency(info.tableNames(), "sf_orm_bcr_meta"));
                assertNotNull(info.columns("order"));
                String snapshotBefore = info.schemaSnapshot();
                String digestBefore = info.schemaDigest();
                assertEquals(64, digestBefore.length());
                assertEquals(digestBefore, info.schemaDigest());
                assertEquals(2, info.schemaDigestComputations());
                String metaBlock = tableBlock(snapshotBefore, "sf_orm_bcr_meta");
                assertTrue(metaBlock.contains("column id INT"));
                assertTrue(metaBlock.contains("column title VARCHAR"));
                assertTrue(metaBlock.contains("column qty BIGINT"));
                assertTrue(metaBlock.contains("column created_at DATETIME"));
                assertFalse(metaBlock.contains("extra_note"));
                assertFalse(snapshotBefore.contains("jdbc:"));
                assertFalse("schema snapshot contains a credential", snapshotBefore.contains(env.get("SF_COMPAT_MYSQL_PASSWORD")));
                assertFalse(snapshotBefore.contains(info.identity()));
                try (Connection connection = open(); Statement statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE " + DBInfo.quoteIdentifier("sf_orm_bcr_meta")
                            + " ADD COLUMN " + DBInfo.quoteIdentifier("extra_note") + " VARCHAR(20) NULL");
                }
                info.refresh();
                assertEquals("VARCHAR", info.columns("sf_orm_bcr_meta").get("extra_note"));
                String digestAfter = info.schemaDigest();
                assertEquals(3, info.schemaDigestComputations());
                assertFalse(digestBefore.equals(digestAfter));
                assertTrue(tableBlock(info.schemaSnapshot(), "sf_orm_bcr_meta").contains("column extra_note VARCHAR"));
                assertEquals(1, frequency(info.tableNames(), "sf_orm_bcr_meta"));
                assertTrue(info.identity().contains("/" + env.get("SF_COMPAT_MYSQL_DATABASE") + "#"));
                long[] oldProbe = legacyProbe(Arrays.asList("sf_orm_bcr_meta", "order"));
                DBInfo.RefreshStats stats = info.lastRefreshStats();
                assertEquals(1, stats.getTablesCalls);
                assertEquals(1, stats.getColumnsCalls);
                assertTrue(oldProbe[0] >= 3);
                System.out.println("SF_ORM_DBINFO_MEASURE oldCalls=" + oldProbe[0]
                        + " oldNanos=" + oldProbe[1]
                        + " newTablesCalls=" + stats.getTablesCalls
                        + " newColumnCalls=" + stats.getColumnsCalls
                        + " newNanos=" + stats.elapsedNanos
                        + " tables=" + stats.tables);
            }
            Settings missing = mysqlSettings("net.csdn.jpa.dbinfo", "sf_orm_bcr_missing");
            try (EnhancementContext.Scope ignored = context.activate()) {
                try {
                    new DBInfo(missing);
                    fail("missing database returned a snapshot");
                } catch (EnhancementFailure failure) {
                    assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
                    assertNotNull(failure.getCause());
                }
            }
        } finally {
            context.close();
        }
    }

    @Test
    public void twoContextsKeepSeparatePoolsAndTheOtherKeepsWorkingAfterShutdown() throws Exception {
        createBusinessTables();
        File classes = compileFixtures();
        URLClassLoader leftLoader = new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader());
        URLClassLoader rightLoader = new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader());
        EnhancementContext left = EnhancementContext.open(leftLoader);
        EnhancementContext right = EnhancementContext.open(rightLoader);
        try {
            Settings settings = smallPoolSettings("net.csdn.jpa.ormfixture");
            Class<?> leftMarker = Class.forName("net.csdn.jpa.ormfixture.shop.ServiceFrameworkPackageAnchor", false, leftLoader);
            Class<?> rightMarker = Class.forName("net.csdn.jpa.ormfixture.shop.ServiceFrameworkPackageAnchor", false, rightLoader);
            JPA.configure(new JPA.CSDNORMConfiguration("development", settings, leftMarker), left);
            JPA.configure(new JPA.CSDNORMConfiguration("development", settings, rightMarker), right);
            assertNull(JPA.defaultContext());

            DataSource leftPool;
            JPAConfig leftConfig;
            Object leftQuill;
            long leftConnection;
            Object savedId;
            try (EnhancementContext.Scope ignored = left.activate()) {
                Class<?> customerType = Class.forName("net.csdn.jpa.ormfixture.shop.Customer", true, leftLoader);
                Object customer = customerType.getDeclaredConstructor().newInstance();
                customerType.getMethod("setName", String.class).invoke(customer, "left-owner");
                assertTrue(((JPABase) customer).save());
                savedId = customerType.getMethod("getId").invoke(customer);
                JPA.getJPAConfig().getJPAContext().closeTx(false);
                leftConfig = JPA.getJPAConfig();
                leftPool = OrmSession.current().mysqlClient().defaultMysqlService().dataSource();
                leftConnection = connectionId(Model.nativeSqlClient().query("SELECT CONNECTION_ID() AS connection_id").get(0));
                leftQuill = QuillDB.ctx();
                EntityManager manager = leftConfig.getJPAContext().em();
                assertNotNull(manager);
                assertTrue(singleNumber(manager.createNativeQuery("SELECT CONNECTION_ID()").getSingleResult()) > 0);
            }

            DataSource rightPool;
            JPAConfig rightConfig;
            Object rightQuill;
            long rightConnection;
            try (EnhancementContext.Scope ignored = right.activate()) {
                Class<?> customerType = Class.forName("net.csdn.jpa.ormfixture.shop.Customer", true, rightLoader);
                Object customer = customerType.getDeclaredConstructor().newInstance();
                customerType.getMethod("setName", String.class).invoke(customer, "right-owner");
                assertTrue(((JPABase) customer).save());
                JPA.getJPAConfig().getJPAContext().closeTx(false);
                rightConfig = JPA.getJPAConfig();
                rightPool = OrmSession.current().mysqlClient().defaultMysqlService().dataSource();
                rightConnection = connectionId(Model.nativeSqlClient().query("SELECT CONNECTION_ID() AS connection_id").get(0));
                rightQuill = QuillDB.ctx();
                assertNotNull(rightConfig.getJPAContext().em());
            }

            assertNotSame(leftPool, rightPool);
            assertNotSame(leftConfig, rightConfig);
            assertNotSame(leftQuill, rightQuill);
            assertTrue(leftConnection > 0);
            assertTrue(rightConnection > 0);
            assertTrue(leftConnection != rightConnection);

            EnhancementContext disabled = EnhancementContext.open(JPA.class.getClassLoader());
            try {
                Settings off = ImmutableSettings.settingsBuilder()
                        .put("development.datasources.mysql.disable", "true")
                        .put("application.model", "net.csdn.jpa.isolation.off")
                        .build();
                JPA.configure(new JPA.CSDNORMConfiguration("development", off, JPA.class), disabled);
                try (EnhancementContext.Scope ignored = disabled.activate()) {
                    try {
                        OrmSession.current().mysqlClient();
                        fail("disabled context borrowed a pool");
                    } catch (EnhancementFailure failure) {
                        assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
                    }
                }
            } finally {
                disabled.close();
            }

            left.close();
            assertFalse(leftConfig.isEnabled());
            assertTrue(((DruidDataSource) leftPool).isClosed());
            try {
                leftConfig.getJPAContext();
                fail("shut down config still opened an entity manager");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("shut down"));
            }
            try {
                leftPool.getConnection();
                fail("closed pool still connected");
            } catch (SQLException expected) {
                assertNotNull(expected);
            }

            try (EnhancementContext.Scope ignored = right.activate()) {
                assertSame(rightPool, OrmSession.current().mysqlClient().defaultMysqlService().dataSource());
                assertFalse(((DruidDataSource) rightPool).isClosed());
                assertTrue(rightConfig.isEnabled());
                Class<?> customerType = Class.forName("net.csdn.jpa.ormfixture.shop.Customer", true, rightLoader);
                Object reloaded = customerType.getMethod("findById", Object.class).invoke(null, savedId);
                assertNotNull(reloaded);
                assertEquals("left-owner", customerType.getMethod("getName").invoke(reloaded));
                Object another = customerType.getDeclaredConstructor().newInstance();
                customerType.getMethod("setName", String.class).invoke(another, "right-after");
                assertTrue(((JPABase) another).save());
                JPA.getJPAConfig().getJPAContext().closeTx(false);
                long stillOpen = connectionId(Model.nativeSqlClient().query("SELECT CONNECTION_ID() AS connection_id").get(0));
                assertTrue(stillOpen > 0);
            }
        } finally {
            if (!left.isClosed()) {
                left.close();
            }
            if (!right.isClosed()) {
                right.close();
            }
            leftLoader.close();
            rightLoader.close();
        }
    }

    @Test
    public void liveEntityManagersReturnToZeroAndShutdownClosesOneFromAnotherThread() throws Exception {
        createBusinessTables();
        File classes = compileFixtures();
        URLClassLoader loader = new URLClassLoader(new URL[]{classes.toURI().toURL()}, JPA.class.getClassLoader());
        EnhancementContext context = EnhancementContext.open(loader);
        try {
            Class<?> marker = Class.forName("net.csdn.jpa.ormfixture.shop.ServiceFrameworkPackageAnchor", false, loader);
            JPA.configure(new JPA.CSDNORMConfiguration("development", smallPoolSettings("net.csdn.jpa.ormfixture"), marker), context);
            try (EnhancementContext.Scope ignored = context.activate()) {
                JPAConfig config = JPA.getJPAConfig();
                Object pool = hibernatePool(config);
                JPAContext finished = config.getJPAContext();
                EntityManager finishedManager = finished.em();
                finished.closeTx(false);
                Field entityManager = JPAContext.class.getDeclaredField("entityManager");
                entityManager.setAccessible(true);
                assertNull(entityManager.get(finished));
                assertFalse(finishedManager.isOpen());
                assertEquals(0, config.openEntityManagerCount());
                assertEquals(0, config.openContextCount());
                assertEquals(0, checkedOut(pool));

                for (int i = 0; i < 30; i++) {
                    config.getJPAContext().closeTx(i % 2 == 0);
                }
                assertEquals(0, config.openEntityManagerCount());
                assertEquals(0, config.openContextCount());
                assertEquals(0, checkedOut(pool));

                JPAContext stale = config.getJPAContext();
                EntityManager manual = stale.em();
                assertEquals(1, checkedOut(pool));
                long heldThread = heldConnectionThread(pool);
                assertFalse(autoCommit(heldConnection(pool)));
                manual.close();
                assertEquals(0, config.openEntityManagerCount());
                assertFalse(manual.isOpen());
                assertEquals(0, checkedOut(pool));
                Connection returned = connectionByThread(pool, heldThread);
                assertNotNull(returned);
                assertTrue(autoCommit(returned));
                JPAContext recovered = config.getJPAContext();
                assertNotSame(stale, recovered);
                assertNull(entityManager.get(stale));
                recovered.closeTx(true);
                assertEquals(0, config.openEntityManagerCount());
                assertEquals(0, checkedOut(pool));

                JPAContext unwrapped = config.getJPAContext();
                EntityManager asSession = unwrapped.em().unwrap(Session.class);
                assertEquals(1, checkedOut(pool));
                long unwrappedThread = heldConnectionThread(pool);
                assertFalse(autoCommit(heldConnection(pool)));
                asSession.close();
                assertEquals(0, config.openEntityManagerCount());
                assertFalse(asSession.isOpen());
                assertEquals(0, checkedOut(pool));
                Connection unwrappedBack = connectionByThread(pool, unwrappedThread);
                assertNotNull(unwrappedBack);
                assertTrue(autoCommit(unwrappedBack));

                final EntityManager[] held = new EntityManager[1];
                final JPAContext[] remote = new JPAContext[1];
                final long[] remoteThread = new long[1];
                final int[] remoteAutoCommit = new int[1];
                final AtomicReference<Throwable> workerFailure = new AtomicReference<Throwable>();
                final CountDownLatch opened = new CountDownLatch(1);
                final CountDownLatch shutdownDone = new CountDownLatch(1);
                Thread worker = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Thread.currentThread().setContextClassLoader(loader);
                        EnhancementContext.Scope scope = context.activate();
                        try {
                            remote[0] = JPA.getJPAConfig().getJPAContext();
                            held[0] = remote[0].em();
                            Object id = held[0].createNativeQuery("SELECT CONNECTION_ID()").getSingleResult();
                            remoteThread[0] = ((Number) id).longValue();
                            Object autoCommit = held[0].createNativeQuery("SELECT @@autocommit").getSingleResult();
                            remoteAutoCommit[0] = ((Number) autoCommit).intValue();
                        } catch (Throwable thrown) {
                            workerFailure.set(thrown);
                        } finally {
                            opened.countDown();
                        }
                        try {
                            if (!shutdownDone.await(20, TimeUnit.SECONDS)) {
                                workerFailure.compareAndSet(null, new IllegalStateException("shutdown did not finish"));
                            }
                        } catch (InterruptedException interrupted) {
                            workerFailure.compareAndSet(null, interrupted);
                        } finally {
                            scope.close();
                        }
                    }
                });
                Throwable testFailure = null;
                try {
                    worker.start();
                    if (!opened.await(20, TimeUnit.SECONDS)) {
                        throw new AssertionError("worker did not open an entity manager");
                    }
                    if (workerFailure.get() != null) {
                        throw new AssertionError(workerFailure.get());
                    }
                    assertEquals(1, config.openEntityManagerCount());
                    assertTrue(held[0].isOpen());
                    assertEquals(1, checkedOut(pool));
                    assertTrue(remoteThread[0] > 0);
                    assertEquals(0, remoteAutoCommit[0]);
                    config.shutdown();
                    assertFalse(held[0].isOpen());
                    assertNull(entityManager.get(remote[0]));
                    assertEquals(0, config.openEntityManagerCount());
                    assertEquals(0, config.openContextCount());
                    assertFalse(config.isEnabled());
                    // stop() closes every physical connection but does not put a
                    // checked-out one back, so this count is still the leak signal.
                    assertEquals(0, checkedOut(pool));
                } catch (Throwable thrown) {
                    testFailure = thrown;
                } finally {
                    shutdownDone.countDown();
                }
                worker.join(20000);
                assertFalse(worker.isAlive());
                if (workerFailure.get() != null && testFailure == null) {
                    testFailure = new AssertionError(workerFailure.get());
                }
                if (testFailure != null) {
                    if (testFailure instanceof Exception) {
                        throw (Exception) testFailure;
                    }
                    if (testFailure instanceof Error) {
                        throw (Error) testFailure;
                    }
                    throw new AssertionError(testFailure);
                }
            }
        } finally {
            if (!context.isClosed()) {
                context.close();
            }
            loader.close();
        }
    }

    @Test
    public void standaloneQuillSelectsWithoutHibernateAndCloseDoesNotTakeTheOtherPool() throws Exception {
        EnhancementContext application = EnhancementContext.open(JPA.class.getClassLoader());
        EnhancementContext bare = EnhancementContext.open(JPA.class.getClassLoader());
        DruidDataSource quillPool = null;
        try {
            Settings settings = smallPoolSettings("net.csdn.jpa.quillstandalone.empty");
            JPA.configure(new JPA.CSDNORMConfiguration("development", settings, JPA.class), application);
            DataSource applicationPool;
            try (EnhancementContext.Scope ignored = application.activate()) {
                applicationPool = OrmSession.current().mysqlClient().defaultMysqlService().dataSource();
                assertTrue(connectionId(Model.nativeSqlClient().query("SELECT CONNECTION_ID() AS connection_id").get(0)) > 0);
            }
            MysqlJdbcContext quill;
            try (EnhancementContext.Scope ignored = bare.activate()) {
                assertNull(OrmSession.currentOrNull());
                assertFalse(JPA.isConfigured());
                quill = QuillDB.createNewCtxByNameFromStr("barequill", snippet("barequill"));
                assertSame(bare, OrmSession.current().context());
                assertFalse(JPA.isConfigured());
                MysqlJdbcContext again = QuillDB.createNewCtxByNameFromStr("barequill", snippet("barequill"));
                assertSame(quill, again);
                assertSame(quill, QuillDB.createNewCtxByNameFromYml("barequill"));
                quillPool = quill.dataSource().unwrap(DruidDataSource.class);
                assertNotSame(applicationPool, quillPool);
                assertNotSame(applicationPool, ((net.csdn.modules.persist.mysql.SharedDataSource) quill.dataSource()).delegate());
                selectOne(quill);
            }
            bare.close();
            assertTrue(quillPool.isClosed());
            try (EnhancementContext.Scope ignored = application.activate()) {
                assertSame(applicationPool, OrmSession.current().mysqlClient().defaultMysqlService().dataSource());
                assertFalse(((DruidDataSource) applicationPool).isClosed());
                assertTrue(connectionId(Model.nativeSqlClient().query("SELECT CONNECTION_ID() AS connection_id").get(0)) > 0);
            }
        } finally {
            if (!bare.isClosed()) {
                bare.close();
            }
            if (!application.isClosed()) {
                application.close();
            }
            QuillDB.close();
        }

        assertNull(EnhancementContext.currentOrNull());
        assertNull(JPA.defaultContext());
        MysqlJdbcContext first = QuillDB.createNewCtxByNameFromStr("compatquill", snippet("compatquill"));
        assertSame(first, QuillDB.createNewCtxByNameFromYml("compatquill"));
        DruidDataSource firstPool = first.dataSource().unwrap(DruidDataSource.class);
        selectOne(first);
        QuillDB.close();
        assertTrue(firstPool.isClosed());
        try {
            firstPool.getConnection();
            fail("closed compat pool was returned again");
        } catch (SQLException expected) {
            assertNotNull(expected);
        }
        MysqlJdbcContext second = QuillDB.createNewCtxByNameFromStr("compatquill", snippet("compatquill"));
        DruidDataSource secondPool = second.dataSource().unwrap(DruidDataSource.class);
        assertNotSame(firstPool, secondPool);
        assertFalse(secondPool.isClosed());
        selectOne(second);
        QuillDB.close();
        assertTrue(secondPool.isClosed());
    }

    @Test
    public void failedPoolConstructionClosesPoolsAlreadyOpened() throws Exception {
        int before = countUserConnections();
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("development.datasources.mysql.disable", "false")
                .put("development.datasources.mysql.host", env.get("SF_COMPAT_MYSQL_HOST"))
                .put("development.datasources.mysql.port", env.get("SF_COMPAT_MYSQL_PORT"))
                .put("development.datasources.mysql.database", env.get("SF_COMPAT_MYSQL_DATABASE"))
                .put("development.datasources.mysql.username", env.get("SF_COMPAT_MYSQL_USER"))
                .put("development.datasources.mysql.password", env.get("SF_COMPAT_MYSQL_PASSWORD"))
                .put("development.datasources.mysql.driver", "com.mysql.jdbc.Driver")
                .put("development.datasources.mysql.initialSize", "1")
                .put("development.datasources.mysql.minIdle", "0")
                .put("development.datasources.mysql.maxActive", "2")
                .put("development.datasources.mysql.jdbc.connectTimeout", "2000")
                .put("development.datasources.multi-mysql.extra.host", "127.0.0.1")
                .put("development.datasources.multi-mysql.extra.port", "1")
                .put("development.datasources.multi-mysql.extra.database", env.get("SF_COMPAT_MYSQL_DATABASE"))
                .put("development.datasources.multi-mysql.extra.username", env.get("SF_COMPAT_MYSQL_USER"))
                .put("development.datasources.multi-mysql.extra.password", env.get("SF_COMPAT_MYSQL_PASSWORD"))
                .put("development.datasources.multi-mysql.extra.driver", "com.mysql.jdbc.Driver")
                .put("development.datasources.multi-mysql.extra.initialSize", "1")
                .put("development.datasources.multi-mysql.extra.minIdle", "0")
                .put("development.datasources.multi-mysql.extra.maxActive", "1")
                .put("development.datasources.multi-mysql.extra.jdbc.connectTimeout", "1000")
                .build();
        try {
            DataSourceManager.forMode(settings, "development");
            fail("closed port was accepted as a second pool");
        } catch (RuntimeException expected) {
            String text = exceptionText(expected);
            assertTrue(text.contains("can not create datasource"));
            assertTrue(text.contains(":1/"));
            assertFalse(text.contains(env.get("SF_COMPAT_MYSQL_PASSWORD")));
        }
        long deadline = System.currentTimeMillis() + 5000L;
        int after = countUserConnections();
        while (after > before && System.currentTimeMillis() < deadline) {
            Thread.sleep(100L);
            after = countUserConnections();
        }
        assertEquals("a pool opened before the failure was still connected", before, after);
    }

    private int countUserConnections() throws Exception {
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM information_schema.processlist WHERE USER = ? AND DB = ?")) {
            statement.setString(1, env.get("SF_COMPAT_MYSQL_USER"));
            statement.setString(2, env.get("SF_COMPAT_MYSQL_DATABASE"));
            try (ResultSet rows = statement.executeQuery()) {
                assertTrue(rows.next());
                return rows.getInt(1);
            }
        }
    }

    private static long connectionId(Map row) {
        Object value = row.get("connection_id");
        if (value == null) {
            value = row.get("CONNECTION_ID");
        }
        return singleNumber(value);
    }

    private static long singleNumber(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private static String exceptionText(Throwable failure) {
        StringBuilder text = new StringBuilder();
        Throwable current = failure;
        while (current != null && text.length() < 2000) {
            text.append(current.getClass().getName()).append(' ');
            if (current.getMessage() != null) {
                text.append(current.getMessage());
            }
            current = current.getCause();
        }
        return text.toString();
    }

    private void selectOne(MysqlJdbcContext quill) throws Exception {
        scala.util.Try probed = quill.probe("SELECT 1");
        assertTrue(String.valueOf(probed), probed.isSuccess());
        try (Connection connection = quill.dataSource().getConnection();
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT 1")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }

    private String snippet(String name) {
        return name + ":\n"
                + "  host: \"" + yaml(env.get("SF_COMPAT_MYSQL_HOST")) + "\"\n"
                + "  port: \"" + yaml(env.get("SF_COMPAT_MYSQL_PORT")) + "\"\n"
                + "  database: \"" + yaml(env.get("SF_COMPAT_MYSQL_DATABASE")) + "\"\n"
                + "  username: \"" + yaml(env.get("SF_COMPAT_MYSQL_USER")) + "\"\n"
                + "  password: \"" + yaml(env.get("SF_COMPAT_MYSQL_PASSWORD")) + "\"\n"
                + "  driver: \"com.mysql.jdbc.Driver\"\n"
                + "  show_sql: \"false\"\n"
                + "  initialSize: \"1\"\n"
                + "  minIdle: \"0\"\n"
                + "  maxActive: \"2\"\n"
                + "  jdbc:\n"
                + "    connectTimeout: \"5000\"\n";
    }

    private static String yaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Settings smallPoolSettings(String modelPackage) {
        return ImmutableSettings.settingsBuilder()
                .put("development.datasources.mysql.disable", "false")
                .put("development.datasources.mysql.host", env.get("SF_COMPAT_MYSQL_HOST"))
                .put("development.datasources.mysql.port", env.get("SF_COMPAT_MYSQL_PORT"))
                .put("development.datasources.mysql.database", env.get("SF_COMPAT_MYSQL_DATABASE"))
                .put("development.datasources.mysql.username", env.get("SF_COMPAT_MYSQL_USER"))
                .put("development.datasources.mysql.password", env.get("SF_COMPAT_MYSQL_PASSWORD"))
                .put("development.datasources.mysql.show_sql", "false")
                .put("development.datasources.mysql.driver", "com.mysql.jdbc.Driver")
                .put("development.datasources.mysql.initialSize", "1")
                .put("development.datasources.mysql.minIdle", "0")
                .put("development.datasources.mysql.maxActive", "2")
                .put("development.datasources.mysql.jdbc.connectTimeout", "5000")
                .put("application.model", modelPackage)
                .build();
    }

    private static int ruleOffset(List<EnhancementPlan.Execution> executions, String ruleId) {
        for (int i = 0; i < executions.size(); i++) {
            if (ruleId.equals(executions.get(i).ruleId())) {
                return i;
            }
        }
        fail("missing rule " + ruleId);
        return -1;
    }

    private void assertRawTypeMatches(Map<String, String> columns) throws Exception {
        try (Connection connection = open()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet resultSet = metaData.getColumns(connection.getCatalog(), null, "sf_orm_bcr_meta", "%")) {
                while (resultSet.next()) {
                    String name = resultSet.getString("COLUMN_NAME");
                    if (!columns.containsKey(name)) {
                        continue;
                    }
                    assertEquals(columns.get(name), DBInfo.normalizeTypeName(resultSet.getString("TYPE_NAME")));
                }
            }
        }
    }

    private long[] legacyProbe(List<String> tables) throws Exception {
        long started = System.nanoTime();
        int calls = 0;
        try (Connection connection = open()) {
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet tablesResult = metaData.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                calls++;
                while (tablesResult.next()) {
                    tablesResult.getString("TABLE_NAME");
                }
            }
            for (int i = 0; i < tables.size(); i++) {
                String sql = "SELECT * FROM " + DBInfo.quoteIdentifier(tables.get(i)) + " LIMIT 1";
                try (PreparedStatement statement = connection.prepareStatement(sql);
                     ResultSet rows = statement.executeQuery()) {
                    calls++;
                    if (rows.next()) {
                        rows.getObject(1);
                    }
                }
            }
        }
        return new long[]{calls, System.nanoTime() - started};
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
        URL url = OrmMysqlBusinessTest.class.getResource("/net/csdn/jpa/ormfixture/shop/Order.java");
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
                if (!"types".equals(files[i].getName())) {
                    collectJava(files[i], sources);
                }
            } else if (files[i].getName().endsWith(".java")) {
                sources.add(files[i]);
            }
        }
    }

    private void createBusinessTables() throws Exception {
        dropTables();
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + DBInfo.quoteIdentifier("sf_orm_bcr_customer")
                    + " (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64) NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8");
            statement.execute("CREATE TABLE " + DBInfo.quoteIdentifier("sf_orm_bcr_shop_order")
                    + " (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, status VARCHAR(32) NULL, region VARCHAR(32) NULL,"
                    + " label VARCHAR(64) NULL, customer_id INT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8");
            statement.execute("CREATE TABLE " + DBInfo.quoteIdentifier("sf_orm_bcr_bill_order")
                    + " (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, code VARCHAR(32) NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8");
            statement.execute("CREATE TABLE " + DBInfo.quoteIdentifier("sf_orm_bcr_photo")
                    + " (id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, root_tag VARCHAR(64) NULL,"
                    + " middle_note VARCHAR(64) NULL, name VARCHAR(64) NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8");
        }
    }

    private void createProbeTables() throws Exception {
        dropTables();
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE " + DBInfo.quoteIdentifier("sf_orm_bcr_meta")
                    + " (id INT NOT NULL PRIMARY KEY, title VARCHAR(32) NULL, qty BIGINT NULL, created_at DATETIME NULL)"
                    + " ENGINE=InnoDB DEFAULT CHARSET=utf8");
            statement.execute("CREATE TABLE " + DBInfo.quoteIdentifier("order")
                    + " (id INT NOT NULL PRIMARY KEY) ENGINE=InnoDB DEFAULT CHARSET=utf8");
        }
    }

    private void dropTables() throws Exception {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS=0");
            for (int i = 0; i < TABLES.length; i++) {
                statement.execute("DROP TABLE IF EXISTS " + DBInfo.quoteIdentifier(TABLES[i]));
            }
            statement.execute("SET FOREIGN_KEY_CHECKS=1");
        }
    }

    private Connection open() throws Exception {
        Class.forName("com.mysql.jdbc.Driver");
        return DriverManager.getConnection(jdbcUrl(), env.get("SF_COMPAT_MYSQL_USER"), env.get("SF_COMPAT_MYSQL_PASSWORD"));
    }

    private String jdbcUrl() {
        return "jdbc:mysql://" + env.get("SF_COMPAT_MYSQL_HOST") + ":" + env.get("SF_COMPAT_MYSQL_PORT")
                + "/" + env.get("SF_COMPAT_MYSQL_DATABASE")
                + "?useUnicode=true&characterEncoding=utf8&connectTimeout=5000";
    }

    private Settings mysqlSettings(String modelPackage, String database) {
        return ImmutableSettings.settingsBuilder()
                .put("development.datasources.mysql.disable", "false")
                .put("development.datasources.mysql.host", env.get("SF_COMPAT_MYSQL_HOST"))
                .put("development.datasources.mysql.port", env.get("SF_COMPAT_MYSQL_PORT"))
                .put("development.datasources.mysql.database", database)
                .put("development.datasources.mysql.username", env.get("SF_COMPAT_MYSQL_USER"))
                .put("development.datasources.mysql.password", env.get("SF_COMPAT_MYSQL_PASSWORD"))
                .put("development.datasources.mysql.show_sql", "false")
                .put("development.datasources.mysql.driver", "com.mysql.jdbc.Driver")
                .put("development.datasources.mysql.jdbc.connectTimeout", "5000")
                .put("application.model", modelPackage)
                .build();
    }

    private static String tableBlock(String snapshot, String table) {
        String header = "\ntable " + table + "\n";
        int start = snapshot.indexOf(header);
        assertTrue(snapshot, start >= 0);
        int next = snapshot.indexOf("\ntable ", start + header.length());
        return next < 0 ? snapshot.substring(start) : snapshot.substring(start, next);
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

    private static boolean autoCommit(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT @@autocommit")) {
            assertTrue(rows.next());
            return rows.getInt(1) != 0;
        }
    }

    private static Connection heldConnection(Object pool) throws Exception {
        List<Connection> held = heldConnections(pool);
        assertEquals(1, held.size());
        return held.get(0);
    }

    private static Connection connectionByThread(Object pool, long threadId) throws Exception {
        Collection<?> available = (Collection<?>) declaredField(pool.getClass(), "availableConnections").get(pool);
        for (Object item : available) {
            Connection connection = (Connection) item;
            if (connectionId(connection) == threadId) {
                return connection;
            }
        }
        return null;
    }

    private static long connectionId(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT CONNECTION_ID()")) {
            assertTrue(rows.next());
            return rows.getLong(1);
        }
    }

    private static Object hibernatePool(JPAConfig config) throws Exception {
        Field factoryField = JPAConfig.class.getDeclaredField("entityManagerFactory");
        factoryField.setAccessible(true);
        Object factory = factoryField.get(config);
        Class<?> implementor = Class.forName("org.hibernate.engine.spi.SessionFactoryImplementor");
        Object sessionFactory = factory.getClass().getMethod("unwrap", Class.class).invoke(factory, implementor);
        Object registry = sessionFactory.getClass().getMethod("getServiceRegistry").invoke(sessionFactory);
        Class<?> providerType = Class.forName("org.hibernate.engine.jdbc.connections.spi.ConnectionProvider");
        Object provider = registry.getClass().getMethod("getService", Class.class).invoke(registry, providerType);
        Object pool = declaredField(provider.getClass(), "pool").get(provider);
        if (pool == null) {
            fail("hibernate connection pool was not created: " + provider.getClass().getName());
        }
        return pool;
    }

    private static int checkedOut(Object pool) throws Exception {
        return heldConnections(pool).size();
    }

    private static long heldConnectionThread(Object pool) throws Exception {
        return connectionId(heldConnection(pool));
    }

    private static List<Connection> heldConnections(Object pool) throws Exception {
        Collection<?> all = (Collection<?>) declaredField(pool.getClass(), "allConnections").get(pool);
        Collection<?> available = (Collection<?>) declaredField(pool.getClass(), "availableConnections").get(pool);
        return heldConnections(all, available);
    }

    private static List<Connection> heldConnections(Collection<?> all, Collection<?> available) {
        Set<Connection> idle = Collections.newSetFromMap(new IdentityHashMap<Connection, Boolean>());
        for (Object item : available) {
            idle.add((Connection) item);
        }
        List<Connection> held = new ArrayList<Connection>();
        for (Object item : all) {
            Connection connection = (Connection) item;
            if (!idle.contains(connection)) {
                held.add(connection);
            }
        }
        return held;
    }

    private static Field declaredField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException missing) {
                current = current.getSuperclass();
            }
        }
        throw new IllegalStateException("missing field " + name + " on " + type.getName());
    }

    private static int frequency(List<String> names, String name) {
        int count = 0;
        for (int i = 0; i < names.size(); i++) {
            if (name.equalsIgnoreCase(names.get(i))) {
                count++;
            }
        }
        return count;
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

    public static final class CountingRule implements EnhancementRule {
        @Override
        public String id() {
            return "orm-test-counter";
        }

        @Override
        public List<String> requires() {
            return Collections.singletonList(EnhancementRuleIds.ENTITY_MAPPING);
        }

        @Override
        public boolean matches(CtClass type, EnhancementContext context) {
            return ModelClass.isModelSubclass(type);
        }

        @Override
        public void apply(CtClass type, EnhancementContext context) {
            EXTRA_RULE_HITS.incrementAndGet();
        }
    }
}
