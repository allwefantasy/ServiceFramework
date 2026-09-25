package net.csdn.bootstrap.extension;

import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.FrameworkExtension;
import net.csdn.bootstrap.loader.impl.ModelLoader;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.enhancer.EnhancementRuleIds;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;

import java.util.Collections;
import java.util.List;

/**
 * MySQL / JPA lifecycle. Loaded only when the datasource is enabled.
 * {@link JPA#configure(JPA.CSDNORMConfiguration, net.csdn.common.enhancer.EnhancementContext)}
 * enhances models. {@link JPA#getJPAConfig()} builds the
 * {@code EntityManagerFactory} before a port is opened. The surrounding
 * application context is closed by its owner; this extension does not close it.
 */
public final class OrmFrameworkExtension implements FrameworkExtension {

    public static final String CAPABILITY = "datasource.mysql";

    private JPA.CSDNORMConfiguration configuration;
    private boolean configured;

    @Override
    public String id() {
        return "orm";
    }

    @Override
    public List<String> provides() {
        return Collections.singletonList(CAPABILITY);
    }

    @Override
    public boolean enabled(Settings settings, ApplicationContext context) {
        return !mysqlDisabled(settings, context);
    }

    @Override
    public void validate(Settings settings, ApplicationContext context) {
        String packages = settings.get("application.model");
        if (packages == null || packages.trim().length() == 0) {
            throw failure("application.model is required when MySQL is enabled");
        }
    }

    @Override
    public void register(Settings settings, ApplicationContext context) {
        configuration = new JPA.CSDNORMConfiguration(
                context.mode().name(),
                settings,
                context.marker());
        try {
            JPA.configure(configuration, context.enhancementContext());
            configured = true;
            ModelLoader.markIfRuleRan(context, EnhancementRuleIds.ENTITY_MAPPING);
        } catch (RuntimeException thrown) {
            closeQuietly(thrown);
            throw thrown;
        }
    }

    @Override
    public void start(Settings settings, ApplicationContext context) {
        if (!configured) {
            return;
        }
        JPA.injector(context.injector());
        JPA.getJPAConfig();
    }

    @Override
    public void close() {
        configured = false;
        configuration = null;
    }

    private void closeQuietly(RuntimeException primary) {
        configured = false;
        configuration = null;
    }

    public static boolean mysqlDisabled(Settings settings, ApplicationContext context) {
        return Boolean.TRUE.equals(settings.getAsBoolean(
                context.mode().name() + ".datasources.mysql.disable",
                Boolean.FALSE));
    }

    private static EnhancementFailure failure(String detail) {
        return new EnhancementFailure(
                EnhancementFailure.Category.CONFIGURATION,
                OrmFrameworkExtension.class.getName(),
                "orm",
                "extension",
                detail,
                null);
    }
}
