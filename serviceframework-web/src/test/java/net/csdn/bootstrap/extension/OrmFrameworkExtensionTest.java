package net.csdn.bootstrap.extension;

import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.FrameworkExtension;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OrmFrameworkExtensionTest {
    private final List<ApplicationContext> contexts = new ArrayList<ApplicationContext>();

    @After
    public void closeContexts() {
        for (int i = contexts.size() - 1; i >= 0; i--) {
            contexts.get(i).close();
        }
        contexts.clear();
    }

    @Test
    public void mysqlCapabilityRemainsAvailableForLegacyConsumers() {
        Assert.assertEquals("datasource.mysql", OrmFrameworkExtension.CAPABILITY);
        Assert.assertEquals("datasource.orm", OrmFrameworkExtension.ORM_CAPABILITY);
        Assert.assertEquals("datasource.postgres", OrmFrameworkExtension.POSTGRES_CAPABILITY);

        OrmFrameworkExtension unprepared = new OrmFrameworkExtension();
        Assert.assertEquals(
                Arrays.asList(OrmFrameworkExtension.CAPABILITY, OrmFrameworkExtension.ORM_CAPABILITY),
                unprepared.provides());

        OrmFrameworkExtension mysql = new OrmFrameworkExtension();
        Assert.assertTrue(mysql.enabled(mysqlSettings(false), context()));
        Assert.assertEquals(
                Arrays.asList(OrmFrameworkExtension.CAPABILITY, OrmFrameworkExtension.ORM_CAPABILITY),
                mysql.provides());

        new ExtensionSession().prepare(
                context(),
                mysqlBuilder(false)
                        .put("application.extensions", RequiresMysql.class.getName())
                        .build(),
                Collections.<FrameworkExtension>emptyList());
    }

    @Test
    public void postgresPrimaryExposesGenericAndPostgresCapabilitiesOnly() {
        OrmFrameworkExtension postgres = new OrmFrameworkExtension();
        Assert.assertTrue(postgres.enabled(postgresSettings(), context()));
        Assert.assertEquals(
                Arrays.asList(OrmFrameworkExtension.ORM_CAPABILITY, OrmFrameworkExtension.POSTGRES_CAPABILITY),
                postgres.provides());

        new ExtensionSession().prepare(
                context(),
                postgresBuilder()
                        .put("application.extensions",
                                RequiresOrm.class.getName() + "," + RequiresPostgres.class.getName())
                        .build(),
                Collections.<FrameworkExtension>emptyList());
    }

    @Test
    public void mysqlRequirementDoesNotResolveAgainstPostgresPrimary() {
        try {
            new ExtensionSession().prepare(
                    context(),
                    postgresBuilder()
                            .put("application.extensions", RequiresMysql.class.getName())
                            .build(),
                    Collections.<FrameworkExtension>emptyList());
            Assert.fail("datasource.mysql resolved against a PostgreSQL primary");
        } catch (EnhancementFailure expected) {
            Assert.assertEquals(EnhancementFailure.Category.DEPENDENCY, expected.getCategory());
            Assert.assertEquals("extension", expected.getPhase());
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains("datasource.mysql"));
        }
    }

    @Test
    public void legacyMysqlHelperOnlyReadsTheMysqlDisableKey() {
        ApplicationContext context = context();
        Assert.assertFalse(OrmFrameworkExtension.mysqlDisabled(mysqlSettings(false), context));
        Assert.assertTrue(OrmFrameworkExtension.mysqlDisabled(mysqlSettings(true), context));
        Assert.assertTrue(OrmFrameworkExtension.mysqlDisabled(postgresSettings(), context));
        Assert.assertTrue(new OrmFrameworkExtension().enabled(postgresSettings(), context));
    }

    private ApplicationContext context() {
        ApplicationContext context = ApplicationContext.open(OrmFrameworkExtensionTest.class);
        contexts.add(context);
        return context;
    }

    private static ImmutableSettings.Builder base() {
        return ImmutableSettings.settingsBuilder()
                .put("mode", "development")
                .put("application.model", "unused")
                .put("development.datasources.mongodb.disable", "true");
    }

    private static Settings mysqlSettings(boolean disabled) {
        return mysqlBuilder(disabled).build();
    }

    private static ImmutableSettings.Builder mysqlBuilder(boolean disabled) {
        return base()
                .put("development.datasources.mysql.disable", String.valueOf(disabled))
                .put("development.datasources.mysql.host", "127.0.0.1")
                .put("development.datasources.mysql.port", "3306")
                .put("development.datasources.mysql.database", "sf_compat");
    }

    private static Settings postgresSettings() {
        return postgresBuilder().build();
    }

    private static ImmutableSettings.Builder postgresBuilder() {
        return base()
                .put("development.datasources.primary", "postgres")
                .put("development.datasources.mysql.disable", "true")
                .put("development.datasources.postgres.host", "127.0.0.1")
                .put("development.datasources.postgres.port", "5432")
                .put("development.datasources.postgres.database", "sf_compat")
                .put("development.datasources.postgres.schema", "public");
    }

    private static final class RequiresMysql implements FrameworkExtension {
        @Override
        public String id() {
            return "requires-mysql";
        }

        @Override
        public List<String> requires() {
            return Collections.singletonList("datasource.mysql");
        }
    }

    private static final class RequiresOrm implements FrameworkExtension {
        @Override
        public String id() {
            return "requires-orm";
        }

        @Override
        public List<String> requires() {
            return Collections.singletonList("datasource.orm");
        }
    }

    private static final class RequiresPostgres implements FrameworkExtension {
        @Override
        public String id() {
            return "requires-postgres";
        }

        @Override
        public List<String> requires() {
            return Collections.singletonList("datasource.postgres");
        }
    }
}
