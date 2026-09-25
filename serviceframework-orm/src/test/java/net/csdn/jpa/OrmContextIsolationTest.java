package net.csdn.jpa;

import net.csdn.common.enhancer.EnhancementContext;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.model.JPABase;
import net.csdn.jpa.model.Model;
import net.csdn.jpa.type.DBInfo;
import net.csdn.modules.persist.mysql.MysqlClient;
import org.junit.After;
import org.junit.Test;
import test.com.william.model.BlogTag;
import test.com.william.model.Tag;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OrmContextIsolationTest {

    @After
    public void shutdown() {
        JPA.shutdown();
    }

    @Test
    public void contextsDoNotShareRegistriesAndSameConfigurationIsIdempotent() {
        Settings first = disabled("net.csdn.jpa.isolation.first");
        Settings second = disabled("net.csdn.jpa.isolation.second");
        EnhancementContext left = EnhancementContext.open(JPA.class.getClassLoader());
        EnhancementContext right = EnhancementContext.open(JPA.class.getClassLoader());
        try {
            JPA.configure(new JPA.CSDNORMConfiguration("development", first, JPA.class), left);
            JPA.configure(new JPA.CSDNORMConfiguration("development", first, JPA.class), left);
            JPA.configure(new JPA.CSDNORMConfiguration("development", second, JPA.class), right);
            try (EnhancementContext.Scope ignored = left.activate()) {
                JPA.models.put(Tag.class.getName(), cast(Tag.class));
                assertEquals(Tag.class, JPA.resolveModel("Tag"));
            }
            try (EnhancementContext.Scope ignored = right.activate()) {
                assertNull(JPA.models.get(Tag.class.getName()));
                JPA.models.put(BlogTag.class.getName(), cast(BlogTag.class));
                assertEquals(BlogTag.class, JPA.resolveModel("BlogTag"));
                try {
                    JPA.resolveModel("Tag");
                    fail("left context model leaked");
                } catch (EnhancementFailure failure) {
                    assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
                }
            }
            try (EnhancementContext.Scope ignored = left.activate()) {
                assertEquals(Tag.class, JPA.resolveModel(Tag.class.getName()));
                assertNull(JPA.models.get(BlogTag.class.getName()));
            }
        } finally {
            left.close();
            right.close();
        }
    }

    @Test
    public void connectionFailureClosesTheConfigurationAndDoesNotKeepItRunning() {
        Settings settings = ImmutableSettings.settingsBuilder()
                .put("development.datasources.mysql.disable", "false")
                .put("development.datasources.mysql.host", "127.0.0.1")
                .put("development.datasources.mysql.port", "1")
                .put("development.datasources.mysql.database", "sf_compat")
                .put("development.datasources.mysql.username", "orm-test")
                .put("development.datasources.mysql.password", "secret-test")
                .put("development.datasources.mysql.jdbc.connectTimeout", "2000")
                .put("application.model", "net.csdn.jpa.isolation.missing")
                .build();
        try {
            JPA.configure(new JPA.CSDNORMConfiguration("development", settings, JPA.class));
            fail("closed port was accepted");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
            assertTrue(failure.getCause() != null || failure.getMessage().contains("schema") || "schema".equals(failure.getPhase()));
            assertTrue(failure.getMessage() == null || !failure.getMessage().contains("secret-test"));
        }
        assertFalse(JPA.isConfigured());
        try {
            JPA.getJPAConfig();
            fail("failed configuration still exposes JPAConfig");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
        }
    }

    @Test
    public void quotesIdentifiersAndNormalizesMysqlTypeNames() {
        assertEquals("`order`", DBInfo.quoteIdentifier("order"));
        assertEquals("`se``lect`", DBInfo.quoteIdentifier("se`lect"));
        assertEquals("INT", DBInfo.normalizeTypeName("INTEGER"));
        assertEquals("INT", DBInfo.normalizeTypeName("INT UNSIGNED"));
        assertEquals("BIGINT", DBInfo.normalizeTypeName("BIGINT"));
        assertEquals("VARCHAR", DBInfo.normalizeTypeName("varchar"));
        assertEquals("DATETIME", DBInfo.normalizeTypeName("datetime"));
    }

    @Test
    public void activeContextWithoutOrmDoesNotBorrowTheDefaultAndScopeRestores() {
        Settings defaults = disabled("net.csdn.jpa.isolation.defaulted");
        JPA.configure(new JPA.CSDNORMConfiguration("development", defaults, JPA.class));
        EnhancementContext defaultContext = JPA.defaultContext();
        JPA.models.put(Tag.class.getName(), cast(Tag.class));
        EnhancementContext empty = EnhancementContext.open(JPA.class.getClassLoader());
        EnhancementContext other = EnhancementContext.open(JPA.class.getClassLoader());
        Settings otherSettings = disabled("net.csdn.jpa.isolation.other");
        JPA.configure(new JPA.CSDNORMConfiguration("development", otherSettings, JPA.class), other);
        assertSame(defaultContext, JPA.defaultContext());
        try {
            try (EnhancementContext.Scope ignored = empty.activate()) {
                assertNull(OrmSession.currentOrNull());
                assertFalse(JPA.isConfigured());
                assertNull(JPA.models.get(Tag.class.getName()));
                try {
                    JPA.settings();
                    fail("empty context borrowed the default settings");
                } catch (EnhancementFailure failure) {
                    assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
                    assertTrue(failure.getMessage().contains("no ORM session"));
                }
                try {
                    JPABase.mysqlClient.dataSource();
                    fail("empty context borrowed a datasource");
                } catch (EnhancementFailure failure) {
                    assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
                }
            }
            assertEquals("net.csdn.jpa.isolation.defaulted", JPA.settings().get("application.model"));
            assertEquals(Tag.class, JPA.models.get(Tag.class.getName()));
            try (EnhancementContext.Scope ignored = other.activate()) {
                assertEquals("net.csdn.jpa.isolation.other", JPA.settings().get("application.model"));
                assertNull(JPA.models.get(Tag.class.getName()));
                try (EnhancementContext.Scope nested = empty.activate()) {
                    assertNull(OrmSession.currentOrNull());
                }
                assertEquals("net.csdn.jpa.isolation.other", JPA.settings().get("application.model"));
            }
            assertEquals("net.csdn.jpa.isolation.defaulted", JPA.settings().get("application.model"));
            assertSame(defaultContext, JPA.defaultContext());
            assertFalse(defaultContext.isClosed());
        } finally {
            empty.close();
            other.close();
        }
    }

    @Test
    public void disabledContextDoesNotBorrowTheDefaultConfiguration() {
        Settings defaults = disabled("net.csdn.jpa.isolation.borrow.default");
        JPA.configure(new JPA.CSDNORMConfiguration("development", defaults, JPA.class));
        JPA.models.put(Tag.class.getName(), cast(Tag.class));
        EnhancementContext disabledContext = EnhancementContext.open(JPA.class.getClassLoader());
        try {
            Settings off = disabled("net.csdn.jpa.isolation.borrow.disabled");
            JPA.configure(new JPA.CSDNORMConfiguration("development", off, JPA.class), disabledContext);
            assertEquals("net.csdn.jpa.isolation.borrow.default", JPA.settings().get("application.model"));
            try (EnhancementContext.Scope ignored = disabledContext.activate()) {
                assertEquals("net.csdn.jpa.isolation.borrow.disabled", JPA.settings().get("application.model"));
                assertNull(JPA.models.get(Tag.class.getName()));
                assertFalse(JPA.isConfigured());
                try {
                    OrmSession.current().mysqlClient();
                    fail("disabled context opened or borrowed a mysql client");
                } catch (EnhancementFailure failure) {
                    assertEquals(EnhancementFailure.Category.CONFIGURATION, failure.getCategory());
                    assertTrue(failure.getDetail().contains("not available"));
                }
            }
            assertEquals(Tag.class, JPA.resolveModel("Tag"));
        } finally {
            disabledContext.close();
        }
    }

    @Test
    public void mysqlBridgeDoesNotCaptureADataSourceDuringClassInitialization() {
        JPA.shutdown();
        assertNotNull(JPABase.mysqlClient);
        final DataSource source = (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) {
                        throw new UnsupportedOperationException(method.getName());
                    }
                });
        MysqlClient direct = new MysqlClient(source);
        assertSame(source, direct.dataSource());
        assertNull(direct.mysqlService("mysql"));
        try {
            JPABase.mysqlClient.dataSource();
            fail("unconfigured bridge returned a datasource");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
        }
        assertSame(source, direct.dataSource());
    }

    @Test
    public void shutdownDropsTheDefaultContext() {
        Settings settings = disabled("net.csdn.jpa.isolation.shutdown");
        JPA.configure(new JPA.CSDNORMConfiguration("development", settings, JPA.class));
        assertNotNull(JPA.defaultContext());
        assertFalse(JPA.defaultContext().isClosed());
        assertFalse(JPA.isConfigured());
        JPA.shutdown();
        assertFalse(JPA.isConfigured());
        try {
            JPA.settings();
            fail("shutdown left the default context usable");
        } catch (EnhancementFailure failure) {
            assertEquals(EnhancementFailure.Category.LIFECYCLE, failure.getCategory());
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<Model> cast(Class<? extends Model> type) {
        return (Class<Model>) type;
    }

    private static Settings disabled(String modelPackage) {
        return ImmutableSettings.settingsBuilder()
                .put("development.datasources.mysql.disable", "true")
                .put("application.model", modelPackage)
                .build();
    }
}
