package net.csdn.bootstrap;

import net.csdn.ServiceFramwork;
import net.csdn.common.collect.Tuple;
import net.csdn.common.enhancer.EnhancementDiagnostics;
import net.csdn.common.env.Environment;
import net.csdn.common.settings.InternalSettingsPreparer;
import net.csdn.common.settings.Settings;

import static net.csdn.common.settings.ImmutableSettings.Builder.EMPTY_SETTINGS;

/**
 * Process entry. {@link #main(String[])} prints, exits, and may join.
 * {@link #configureSystem()} and {@link #configureSystem(Settings, Class)} throw
 * and return; they do not exit the JVM or join a server thread.
 */
public class Bootstrap {

    public static void main(String[] args) {
        try {
            ApplicationContext context = configureSystem();
            if (context.shouldJoin()) {
                Thread.currentThread().join();
            }
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(3);
        }
    }

    public static ApplicationContext configureSystem() throws Exception {
        Tuple<Settings, Environment> tuple = InternalSettingsPreparer.prepareSettings(
                EMPTY_SETTINGS,
                ServiceFramwork.applicaionYamlName());
        return ApplicationContext.bootstrapDefault(tuple.v1());
    }

    /**
     * Starts one application on {@code marker}'s loader. A running context for
     * that loader is returned as-is. Failures propagate to the caller.
     */
    public static ApplicationContext configureSystem(Settings settings, Class<?> marker) throws Exception {
        return configureSystem(settings, marker, null);
    }

    /**
     * Same as {@link #configureSystem(Settings, Class)} but records enhancement
     * into {@code diagnostics} when this call creates the application. Null keeps
     * diagnostics off. Source and class-file dumps stay off unless the caller
     * sets those flags on {@code diagnostics}.
     */
    public static ApplicationContext configureSystem(
            Settings settings,
            Class<?> marker,
            EnhancementDiagnostics diagnostics) throws Exception {
        if (diagnostics != null && diagnostics.enabled() && diagnostics.configVersion() == null) {
            diagnostics.noteSafeMetadata(WebEnhancementMetadata.APPLICATION_REVISION, null);
        }
        ApplicationContext context = ApplicationContext.open(marker, diagnostics);
        context.start(settings);
        return context;
    }

    public static void shutdown() {
        ApplicationContext current = ApplicationContext.defaultContext();
        if (current != null) {
            current.close();
        }
    }
}
