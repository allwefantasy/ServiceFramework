package net.csdn.common.logging.log4j;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.properties.PropertiesConfigurationBuilder;
import net.csdn.common.env.Environment;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;

import java.util.Map;
import java.util.Properties;

import static net.csdn.common.settings.ImmutableSettings.settingsBuilder;

/**
 * Loads {@code config/logging.yml} and installs it as the process Log4j 2 configuration.
 * <p>
 * The shipped file is still the Log4j 1 shape: {@code rootLogger: INFO,console,file},
 * appender {@code type} values such as {@code console} and {@code dailyRollingFile},
 * layout {@code conversionPattern}, and {@code datePattern}. Those keys are translated
 * to Log4j 2 properties ({@code Console}, {@code RollingFile}, {@code pattern},
 * a time-based rollover). A document that already uses Log4j 2 keys
 * ({@code rootLogger.level}, plugin {@code type} names) is passed through.
 * Only logging keys are sent to Log4j. Application settings are used to resolve
 * {@code ${...}} placeholders and are not copied into the logger configuration.
 * <p>
 * {@link #configure(Settings)} sets the process-wide loaded flag only after the
 * configuration is installed. A failure leaves the flag clear so a later call can
 * retry with a valid file. {@link #reset()} clears the flag without rolling back
 * a configuration that was already installed.
 */
public class LogConfigurator {
    private static boolean loaded;

    public static void configure(Settings settings) {
        if (loaded) {
            return;
        }
        LoggerContext context = (LoggerContext) LogManager.getContext(true);
        apply(settings, context, true);
        loaded = true;
    }

    /**
     * Clears the process-wide success flag. The next {@link #configure(Settings)}
     * runs again. Does not hide a failed attempt and does not restore the previous
     * Log4j configuration.
     */
    public static void reset() {
        loaded = false;
    }

    static boolean isLoaded() {
        return loaded;
    }

    /**
     * Builds and installs the logging document on {@code context} without touching
     * the process-wide flag. Tests use this with a context that is not the JVM default.
     */
    static Configuration apply(Settings settings, LoggerContext context) {
        return apply(settings, context, false);
    }

    private static Configuration apply(Settings settings, LoggerContext context, boolean initialize) {
        if (settings == null) {
            throw new IllegalStateException("settings are required");
        }
        if (context == null) {
            throw new IllegalStateException("logger context is required");
        }
        Properties properties = translate(settings);
        Configuration config;
        try {
            config = new PropertiesConfigurationBuilder()
                    .setConfigurationSource(ConfigurationSource.NULL_SOURCE)
                    .setRootProperties(properties)
                    .setLoggerContext(context)
                    .build();
        } catch (RuntimeException thrown) {
            throw rejected(thrown);
        }
        context.setConfiguration(config);
        if (initialize) {
            Configurator.initialize(config);
        }
        return config;
    }

    static Properties translate(Settings settings) {
        Settings logging = loggingDocument(settings);
        if (isNative(logging)) {
            Properties nativeProperties = new Properties();
            for (Map.Entry<String, String> entry : logging.getAsMap().entrySet()) {
                nativeProperties.setProperty(entry.getKey(), entry.getValue());
            }
            return nativeProperties;
        }
        Properties properties = new Properties();
        properties.setProperty("status", "error");
        properties.setProperty("name", "ServiceFramework");
        translateRoot(logging.get("rootLogger"), properties);
        Map<String, Settings> appenders;
        try {
            appenders = logging.getGroups("appender");
        } catch (RuntimeException thrown) {
            throw rejected(thrown);
        }
        for (Map.Entry<String, Settings> entry : appenders.entrySet()) {
            translateAppender(entry.getKey(), entry.getValue(), properties);
        }
        return properties;
    }

    private static Settings loggingDocument(Settings application) {
        Environment environment = new Environment(application);
        ImmutableSettings.Builder merged = settingsBuilder().put(application);
        merged.loadFromUrl(environment.resolveConfig("logging.yml")).replacePropertyPlaceholders();
        Settings resolved = merged.build();
        ImmutableSettings.Builder logging = settingsBuilder();
        for (Map.Entry<String, String> entry : resolved.getAsMap().entrySet()) {
            if (isLoggingKey(entry.getKey())) {
                logging.put(entry.getKey(), entry.getValue());
            }
        }
        return logging.build();
    }

    private static boolean isLoggingKey(String key) {
        return "rootLogger".equals(key)
                || key.startsWith("rootLogger.")
                || key.startsWith("appender.")
                || key.startsWith("appenders.")
                || key.startsWith("logger.")
                || key.startsWith("loggers.")
                || "status".equals(key)
                || "name".equals(key)
                || key.startsWith("property.")
                || key.startsWith("properties.");
    }

    private static boolean isNative(Settings logging) {
        if (logging.get("rootLogger.level") != null) {
            return true;
        }
        String root = logging.get("rootLogger");
        if (root != null && root.indexOf(',') < 0 && root.indexOf('.') >= 0) {
            return true;
        }
        for (Map.Entry<String, String> entry : logging.getAsMap().entrySet()) {
            String key = entry.getKey();
            if (key.endsWith(".type") && isPluginName(entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isPluginName(String value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        char first = value.charAt(0);
        return first >= 'A' && first <= 'Z';
    }

    private static void translateRoot(String root, Properties properties) {
        if (root == null || root.trim().length() == 0) {
            throw new IllegalStateException("logging rootLogger is required");
        }
        String[] parts = root.split(",");
        String level = parts[0].trim();
        if (!knownLevel(level)) {
            throw new IllegalStateException("unsupported logging level");
        }
        properties.setProperty("rootLogger.level", level);
        for (int i = 1; i < parts.length; i++) {
            String name = parts[i].trim();
            if (name.length() == 0) {
                throw new IllegalStateException("logging rootLogger appender name is empty");
            }
            properties.setProperty("rootLogger.appenderRef." + name + ".ref", name);
        }
    }

    private static boolean knownLevel(String level) {
        return Level.getLevel(level) != null;
    }

    private static void translateAppender(String name, Settings spec, Properties properties) {
        String prefix = "appender." + name + ".";
        String type = spec.get("type");
        if (type == null || type.trim().length() == 0) {
            throw new IllegalStateException("logging appender type is required");
        }
        String plugin = appenderPlugin(type.trim());
        properties.setProperty(prefix + "type", plugin);
        properties.setProperty(prefix + "name", name);
        String layoutType = spec.get("layout.type");
        String pattern = spec.get("layout.conversionPattern");
        if (pattern == null) {
            pattern = spec.get("layout.pattern");
        }
        if (layoutType != null || pattern != null) {
            properties.setProperty(prefix + "layout.type", layoutPlugin(layoutType));
            if ("PatternLayout".equals(layoutPlugin(layoutType))) {
                if (pattern == null || pattern.length() == 0) {
                    pattern = "%level - %msg%n";
                }
                properties.setProperty(prefix + "layout.pattern", pattern);
            }
        }
        String threshold = spec.get("threshold");
        if (threshold != null && threshold.trim().length() > 0) {
            if (!knownLevel(threshold.trim())) {
                throw new IllegalStateException("unsupported logging level");
            }
            properties.setProperty(prefix + "filter.threshold.type", "ThresholdFilter");
            properties.setProperty(prefix + "filter.threshold.level", threshold.trim());
            properties.setProperty(prefix + "filter.threshold.onMatch", "ACCEPT");
            properties.setProperty(prefix + "filter.threshold.onMismatch", "DENY");
        }
        String file = spec.get("file");
        if (file == null) {
            file = spec.get("fileName");
        }
        if ("File".equals(plugin)) {
            if (file == null || file.trim().length() == 0) {
                throw new IllegalStateException("logging file appender path is required");
            }
            properties.setProperty(prefix + "fileName", file);
        } else if ("RollingFile".equals(plugin)) {
            if (file == null || file.trim().length() == 0) {
                throw new IllegalStateException("logging file appender path is required");
            }
            properties.setProperty(prefix + "fileName", file);
            properties.setProperty(prefix + "filePattern", filePattern(file, spec.get("datePattern")));
            properties.setProperty(prefix + "policies.type", "Policies");
            properties.setProperty(prefix + "policies.time.type", "TimeBasedTriggeringPolicy");
            properties.setProperty(prefix + "policies.time.interval", "1");
            properties.setProperty(prefix + "policies.time.modulate", "true");
        }
    }

    private static String appenderPlugin(String type) {
        if ("console".equals(type)) {
            return "Console";
        }
        if ("file".equals(type)) {
            return "File";
        }
        if ("dailyRollingFile".equals(type) || "rollingFile".equals(type)) {
            return "RollingFile";
        }
        if ("null".equals(type)) {
            return "Null";
        }
        if (isPluginName(type)) {
            return type;
        }
        throw new IllegalStateException("unsupported logging appender type");
    }

    private static String layoutPlugin(String type) {
        if (type == null || type.trim().length() == 0 || "pattern".equals(type) || "consolePattern".equals(type)) {
            return "PatternLayout";
        }
        if ("simple".equals(type)) {
            return "PatternLayout";
        }
        if ("html".equals(type)) {
            return "HtmlLayout";
        }
        if ("xml".equals(type)) {
            return "XmlLayout";
        }
        if (isPluginName(type)) {
            return type;
        }
        throw new IllegalStateException("unsupported logging layout type");
    }

    /**
     * Log4j 1 {@code datePattern} uses {@code SimpleDateFormat} quotes.
     * {@code '.'yyyy-MM-dd} becomes a Log4j 2 file pattern suffix {@code .%d{yyyy-MM-dd}}.
     */
    static String filePattern(String fileName, String datePattern) {
        String pattern = datePattern == null ? "'.'yyyy-MM-dd" : datePattern.trim();
        String stripped = pattern.replace("'", "");
        int dateStart = 0;
        while (dateStart < stripped.length() && !isDateField(stripped.charAt(dateStart))) {
            dateStart++;
        }
        if (dateStart >= stripped.length()) {
            throw new IllegalStateException("logging datePattern has no date field");
        }
        return fileName + stripped.substring(0, dateStart) + "%d{" + stripped.substring(dateStart) + "}";
    }

    private static boolean isDateField(char c) {
        return c == 'y' || c == 'M' || c == 'd' || c == 'H' || c == 'm' || c == 's'
                || c == 'S' || c == 'w' || c == 'W' || c == 'D' || c == 'F' || c == 'E'
                || c == 'k' || c == 'K' || c == 'h' || c == 'a' || c == 'z' || c == 'Z';
    }

    private static IllegalStateException rejected(RuntimeException thrown) {
        return new IllegalStateException("Log4j2 rejected the translated logging configuration", thrown);
    }
}
