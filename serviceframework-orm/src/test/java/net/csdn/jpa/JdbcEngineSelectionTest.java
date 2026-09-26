package net.csdn.jpa;

import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.JdbcEngine;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.type.DBInfo;
import net.csdn.jpa.type.impl.PostgresType;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JdbcEngineSelectionTest {
    @Test
    public void defaultMysqlSelectionKeepsExistingShape() {
        Settings settings = base()
                .put("development.datasources.mysql.host", "localhost")
                .put("development.datasources.mysql.port", "3306")
                .put("development.datasources.mysql.database", "sf_compat")
                .put("development.datasources.mysql.username", "sf_compat")
                .put("development.datasources.mysql.password", "not-a-secret")
                .build();
        JdbcEngine.Selection selection = JdbcEngine.primary(settings, "development");
        assertEquals(JdbcEngine.MYSQL, selection.engine());
        Map<String, String> properties = JPA.properties(selection.group(), selection.engine());
        assertEquals("com.mysql.jdbc.Driver", properties.get("driver_class"));
        assertEquals("org.hibernate.dialect.MySQLDialect", properties.get("dialect"));
        assertEquals("jdbc:mysql://localhost:3306/sf_compat?useUnicode=true&characterEncoding=utf8",
                properties.get("url"));
        assertFalse(properties.containsKey("hibernate.default_schema"));
    }

    @Test
    public void postgresPrimaryCarriesSchemaToJdbcAndHibernate() {
        Settings settings = base()
                .put("development.datasources.primary", "postgresql")
                .put("development.datasources.postgres.host", "127.0.0.1")
                .put("development.datasources.postgres.port", "65432")
                .put("development.datasources.postgres.database", "sf_compat")
                .put("development.datasources.postgres.schema", "app")
                .put("development.datasources.postgres.username", "sf_compat")
                .put("development.datasources.postgres.password", "not-a-secret")
                .put("development.datasources.postgres.jdbc.sslmode", "disable")
                .build();
        JdbcEngine.Selection selection = JdbcEngine.primary(settings, "development");
        assertEquals(JdbcEngine.POSTGRES, selection.engine());
        assertTrue(selection.explicit());
        Map<String, String> properties = JPA.properties(selection.group(), selection.engine());
        assertEquals("org.postgresql.Driver", properties.get("driver_class"));
        assertEquals("org.hibernate.dialect.PostgreSQL95Dialect", properties.get("dialect"));
        assertEquals("app", properties.get("hibernate.default_schema"));
        assertEquals("jdbc:postgresql://127.0.0.1:65432/sf_compat?currentSchema=app&sslmode=disable",
                properties.get("url"));
    }

    @Test
    public void postgresMixedCaseSchemaIsQuotedForHibernateAndJdbc() {
        Settings settings = base()
                .put("development.datasources.primary", "postgres")
                .put("development.datasources.postgres.host", "127.0.0.1")
                .put("development.datasources.postgres.port", "65432")
                .put("development.datasources.postgres.database", "sf_compat")
                .put("development.datasources.postgres.schema", "SfvMixed")
                .put("development.datasources.postgres.username", "sf_compat")
                .put("development.datasources.postgres.password", "not-a-secret")
                .build();
        JdbcEngine.Selection selection = JdbcEngine.primary(settings, "development");
        Map<String, String> properties = JPA.properties(selection.group(), selection.engine());
        assertEquals("\"SfvMixed\"", properties.get("hibernate.default_schema"));
        assertEquals("jdbc:postgresql://127.0.0.1:65432/sf_compat?currentSchema=%22SfvMixed%22",
                properties.get("url"));
    }

    @Test
    public void postgresQuotedSchemaConfigAndExplicitCurrentSchemaMatchQuotedForm() {
        Settings settings = base()
                .put("development.datasources.primary", "postgres")
                .put("development.datasources.postgres.host", "127.0.0.1")
                .put("development.datasources.postgres.port", "65432")
                .put("development.datasources.postgres.database", "sf_compat")
                .put("development.datasources.postgres.schema", "\"SfvMixed\"")
                .put("development.datasources.postgres.username", "sf_compat")
                .put("development.datasources.postgres.password", "not-a-secret")
                .put("development.datasources.postgres.jdbc.currentSchema", "SfvMixed")
                .put("development.datasources.postgres.jdbc.sslmode", "disable")
                .build();
        JdbcEngine.Selection selection = JdbcEngine.primary(settings, "development");
        assertEquals("SfvMixed", JdbcEngine.schema(selection.group()));
        Map<String, String> properties = JPA.properties(selection.group(), selection.engine());
        assertEquals("\"SfvMixed\"", properties.get("hibernate.default_schema"));
        assertEquals("jdbc:postgresql://127.0.0.1:65432/sf_compat?currentSchema=%22SfvMixed%22&sslmode=disable",
                properties.get("url"));
    }

    @Test
    public void postgresCurrentSchemaDisagreementStillFails() {
        Settings settings = base()
                .put("development.datasources.primary", "postgres")
                .put("development.datasources.postgres.host", "127.0.0.1")
                .put("development.datasources.postgres.port", "65432")
                .put("development.datasources.postgres.database", "sf_compat")
                .put("development.datasources.postgres.schema", "SfvMixed")
                .put("development.datasources.postgres.username", "sf_compat")
                .put("development.datasources.postgres.password", "not-a-secret")
                .put("development.datasources.postgres.jdbc.currentSchema", "other")
                .build();
        JdbcEngine.Selection selection = JdbcEngine.primary(settings, "development");
        try {
            JPA.properties(selection.group(), selection.engine());
        } catch (EnhancementFailure expected) {
            assertTrue(expected.getMessage().contains("currentSchema"));
            return;
        }
        throw new AssertionError("mismatched jdbc.currentSchema was accepted");
    }

    @Test
    public void invalidEngineAndMissingSelectedGroupFailClearly() {
        expectConfigFailure(ImmutableSettings.settingsBuilder()
                .put("mode", "development")
                .put("development.datasources.primary", "sqlite")
                .build());
        expectConfigFailure(ImmutableSettings.settingsBuilder()
                .put("mode", "development")
                .put("development.datasources.primary", "postgres")
                .build());
    }

    @Test
    public void postgresIdentifiersAndTypesAreEngineAware() {
        assertEquals("`we``ird`", DBInfo.quoteIdentifier("we`ird"));
        assertEquals("\"Mi\"\"xed\"", DBInfo.quoteIdentifier(JdbcEngine.POSTGRES, "Mi\"xed"));
        assertEquals("UUID", DBInfo.normalizeTypeName(JdbcEngine.POSTGRES, "uuid"));
        assertEquals("NUMERIC", DBInfo.normalizeTypeName(JdbcEngine.POSTGRES, "numeric(10,2)"));
        assertEquals("TIMESTAMPTZ", DBInfo.normalizeTypeName(JdbcEngine.POSTGRES, "timestamp with time zone"));
        PostgresType type = new PostgresType();
        assertEquals("java.util.UUID", type.typeToJava("UUID").v2());
        assertEquals("java.math.BigDecimal", type.typeToJava("NUMERIC").v2());
        assertEquals("byte[]", type.typeToJava("BYTEA").v2());
    }

    @Test
    public void datasourceGroupEngineMustMatch() {
        Settings postgres = ImmutableSettings.settingsBuilder()
                .put("engine", "postgres")
                .put("host", "127.0.0.1")
                .put("port", "5432")
                .put("database", "sf_compat")
                .build();
        assertEquals(JdbcEngine.POSTGRES, JdbcEngine.groupEngine(postgres, JdbcEngine.POSTGRES));
        try {
            JPA.properties(postgres, JdbcEngine.MYSQL);
        } catch (EnhancementFailure expected) {
            assertTrue(expected.getMessage().contains("does not match expected engine"));
            return;
        }
        throw new AssertionError("postgres group was accepted as mysql");
    }

    private static ImmutableSettings.Builder base() {
        return ImmutableSettings.settingsBuilder().put("mode", "development");
    }

    private static void expectConfigFailure(Settings settings) {
        try {
            JdbcEngine.primary(settings, "development");
        } catch (EnhancementFailure expected) {
            assertEquals(EnhancementFailure.Category.CONFIGURATION, expected.getCategory());
            return;
        }
        throw new AssertionError("configuration was accepted");
    }
}
