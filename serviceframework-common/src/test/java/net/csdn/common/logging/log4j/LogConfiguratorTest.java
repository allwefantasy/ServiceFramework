package net.csdn.common.logging.log4j;

import net.csdn.common.settings.ImmutableSettings;
import net.csdn.common.settings.Settings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class LogConfiguratorTest {
    private static final String SECRET = "super-secret-db-value";

    @Test
    public void legacySampleConfiguresConsoleAndRollingFileWithoutSettings() throws Exception {
        File root = new File("target/logging-sample-" + System.nanoTime());
        File conf = new File(root, "conf");
        File logs = new File(root, "logs");
        assertTrue(conf.mkdirs());
        assertTrue(logs.mkdirs());
        copySample(new File(conf, "logging.yml"));
        Settings settings = settings(conf, logs, "sf-log-sample");
        LoggerContext isolated = new LoggerContext("sf-log-sample-" + System.nanoTime());
        isolated.start();
        try {
            Properties translated = LogConfigurator.translate(settings);
            String dumped = translated.toString();
            assertFalse(dumped, dumped.contains(SECRET));
            assertFalse(dumped, dumped.contains("datasources.password"));
            Configuration configuration = LogConfigurator.apply(settings, isolated);
            assertFalse(String.valueOf(configuration.getAppenders()), String.valueOf(configuration.getAppenders()).contains(SECRET));
            Appender console = configuration.getAppender("console");
            Appender file = configuration.getAppender("file");
            assertTrue(console instanceof ConsoleAppender);
            assertTrue(file instanceof RollingFileAppender);
            RollingFileAppender rolling = (RollingFileAppender) file;
            assertTrue(rolling.getFileName(), rolling.getFileName().contains("sf-log-sample.log"));
            assertTrue(rolling.getFilePattern(), rolling.getFilePattern().contains("%d{yyyy-MM-dd}"));
            assertEquals(org.apache.logging.log4j.Level.INFO, configuration.getRootLogger().getLevel());
            assertNotNull(configuration.getRootLogger().getAppenders().get("console"));
            assertNotNull(configuration.getRootLogger().getAppenders().get("file"));
            assertFalse(LogConfigurator.isLoaded());
        } finally {
            isolated.stop();
        }
    }

    @Test
    public void badConfigurationDoesNotStickAndValidFileCanRetry() throws Exception {
        LoggerContext process = (LoggerContext) LogManager.getContext(true);
        Configuration previous = process.getConfiguration();
        File root = new File("target/logging-retry-" + System.nanoTime());
        File badConf = new File(root, "bad");
        File goodConf = new File(root, "good");
        File logs = new File(root, "logs");
        assertTrue(badConf.mkdirs());
        assertTrue(goodConf.mkdirs());
        assertTrue(logs.mkdirs());
        write(new File(badConf, "logging.yml"),
                "rootLogger: INFO,console\n"
                        + "appender:\n"
                        + "  console:\n"
                        + "    type: notARealAppender\n"
                        + "    layout:\n"
                        + "      type: pattern\n"
                        + "      conversionPattern: \"%m%n\"\n");
        copySample(new File(goodConf, "logging.yml"));
        LogConfigurator.reset();
        try {
            try {
                LogConfigurator.configure(settings(badConf, logs, "sf-log-bad"));
                fail("bad logging configuration was accepted");
            } catch (RuntimeException thrown) {
                assertFalse(String.valueOf(thrown), String.valueOf(thrown).contains(SECRET));
            }
            assertFalse(LogConfigurator.isLoaded());
            LogConfigurator.configure(settings(goodConf, logs, "sf-log-good"));
            assertTrue(LogConfigurator.isLoaded());
            Configuration installed = ((LoggerContext) LogManager.getContext(true)).getConfiguration();
            assertTrue(installed.getAppender("console") instanceof ConsoleAppender);
            assertTrue(installed.getAppender("file") instanceof RollingFileAppender);
            LogConfigurator.configure(settings(badConf, logs, "sf-log-bad-again"));
            assertTrue(((LoggerContext) LogManager.getContext(true)).getConfiguration().getAppender("file")
                    instanceof RollingFileAppender);
            LogConfigurator.reset();
            try {
                LogConfigurator.configure(settings(badConf, logs, "sf-log-bad-reset"));
                fail("reset did not allow the bad file to fail again");
            } catch (RuntimeException expected) {
                assertFalse(LogConfigurator.isLoaded());
            }
        } finally {
            LogConfigurator.reset();
            process.setConfiguration(previous);
        }
    }

    private static Settings settings(File conf, File logs, String cluster) {
        return ImmutableSettings.settingsBuilder()
                .put("path.conf", conf.getAbsolutePath())
                .put("path.logs", logs.getAbsolutePath())
                .put("cluster.name", cluster)
                .put("datasources.password", SECRET)
                .put("datasources.url", "jdbc:mysql://root:" + SECRET + "@127.0.0.1/app")
                .build();
    }

    private static void copySample(File dest) throws Exception {
        File sample = new File("config/logging.yml");
        if (!sample.isFile()) {
            sample = new File("../config/logging.yml");
        }
        assertTrue(sample.getAbsolutePath(), sample.isFile());
        write(dest, read(sample));
    }

    private static String read(File file) throws Exception {
        java.io.FileInputStream input = new java.io.FileInputStream(file);
        try {
            byte[] bytes = new byte[(int) file.length()];
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    break;
                }
                offset += count;
            }
            return new String(bytes, "UTF-8");
        } finally {
            input.close();
        }
    }

    private static void write(File dest, String text) throws Exception {
        FileOutputStream output = new FileOutputStream(dest);
        OutputStreamWriter writer = new OutputStreamWriter(output, "UTF-8");
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }
}
