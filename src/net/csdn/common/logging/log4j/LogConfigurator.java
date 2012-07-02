package net.csdn.common.logging.log4j;

import com.google.common.collect.ImmutableMap;
import net.csdn.common.collect.MapBuilder;
import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import net.csdn.env.Environment;
import org.apache.log4j.PropertyConfigurator;

import java.util.Map;
import java.util.Properties;

import static net.csdn.common.settings.ImmutableSettings.settingsBuilder;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午2:11
 */
public class LogConfigurator {
    private static boolean loaded;

    private static ImmutableMap<String, String> replacements = new MapBuilder<String, String>()
            .put("console", "net.csdn.common.logging.log4j.ConsoleAppender")
            .put("async", "org.apache.log4j.AsyncAppender")
            .put("dailyRollingFile", "org.apache.log4j.DailyRollingFileAppender")
            .put("externallyRolledFile", "org.apache.log4j.ExternallyRolledFileAppender")
            .put("file", "org.apache.log4j.FileAppender")
            .put("hadoo", "org.apache.log4j.FileAppender")
            .put("jdbc", "org.apache.log4j.jdbc.JDBCAppender")
            .put("jms", "org.apache.log4j.net.JMSAppender")
            .put("lf5", "org.apache.log4j.lf5.LF5Appender")
            .put("ntevent", "org.apache.log4j.nt.NTEventLogAppender")
            .put("null", "org.apache.log4j.NullAppender")
            .put("rollingFile", "org.apache.log4j.RollingFileAppender")
            .put("smtp", "org.apache.log4j.net.SMTPAppender")
            .put("socket", "org.apache.log4j.net.SocketAppender")
            .put("socketHub", "org.apache.log4j.net.SocketHubAppender")
            .put("syslog", "org.apache.log4j.net.SyslogAppender")
            .put("telnet", "org.apache.log4j.net.TelnetAppender")
                    // layouts
            .put("simple", "org.apache.log4j.SimpleLayout")
            .put("html", "org.apache.log4j.HTMLLayout")
            .put("pattern", "org.apache.log4j.PatternLayout")
            .put("consolePattern", "net.csdn.common.logging.log4j.JLinePatternLayout")
            .put("ttcc", "org.apache.log4j.TTCCLayout")
            .put("xml", "org.apache.log4j.XMLLayout")
            .immutableMap();

    public static void configure(Settings settings) {
        if (loaded) {
            return;
        }
        loaded = true;
        Environment environment = new Environment(settings);
        ImmutableSettings.Builder settingsBuilder = settingsBuilder().put(settings);

        settingsBuilder.loadFromUrl(environment.resolveConfig("logging.yml"))
                .replacePropertyPlaceholders();

        Properties props = new Properties();
        for (Map.Entry<String, String> entry : settingsBuilder.build().getAsMap().entrySet()) {
            String key = "log4j." + entry.getKey();
            String value = entry.getValue();

            if (replacements.containsKey(value)) {
                value = replacements.get(value);
            }

            if (key.endsWith(".value")) {
                props.setProperty(key.substring(0, key.length() - ".value".length()), value);
            } else if (key.endsWith(".type")) {
                props.setProperty(key.substring(0, key.length() - ".type".length()), value);
            } else {
                props.setProperty(key, value);
            }
        }
        PropertyConfigurator.configure(props);
    }
}
