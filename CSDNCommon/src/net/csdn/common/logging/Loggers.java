package net.csdn.common.logging;

import net.csdn.common.Classes;
import net.csdn.common.settings.Settings;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;

/**
 * BlogInfo: william
 * Date: 11-9-1
 * Time: 下午3:28
 */
public class Loggers {
    private final static String commonPrefix = System.getProperty("cs.logger.prefix", "");

    public static final String SPACE = " ";

    private static boolean consoleLoggingEnabled = true;

    public static void disableConsoleLogging() {
        consoleLoggingEnabled = false;
    }

    public static void enableConsoleLogging() {
        consoleLoggingEnabled = true;
    }

    public static boolean consoleLoggingEnabled() {
        return consoleLoggingEnabled;
    }


    public static CSLogger getLogger(Class clazz, Settings settings, String... prefixes) {
        return getLogger(buildClassLoggerName(clazz), settings, prefixes);
    }

    public static CSLogger getLogger(String loggerName, Settings settings, String... prefixes) {
        List<String> prefixesList = newArrayList();
        if (settings.getAsBoolean("logger.logHostAddress", false)) {
            try {
                prefixesList.add(InetAddress.getLocalHost().getHostAddress());
            } catch (UnknownHostException e) {
                // ignore
            }
        }
        if (settings.getAsBoolean("logger.logHostName", false)) {
            try {
                prefixesList.add(InetAddress.getLocalHost().getHostName());
            } catch (UnknownHostException e) {
                // ignore
            }
        }
        String name = settings.get("name");
        if (name != null) {
            prefixesList.add(name);
        }
        if (prefixes != null && prefixes.length > 0) {
            prefixesList.addAll(asList(prefixes));
        }
        return getLogger(getLoggerName(loggerName), prefixesList.toArray(new String[prefixesList.size()]));
    }

    public static CSLogger getLogger(CSLogger parentLogger, String s) {
        return getLogger(parentLogger.getName() + s, parentLogger.getPrefix());
    }

    public static CSLogger getLogger(String s) {
        return CSLoggerFactory.getLogger(s);
    }

    public static CSLogger getLogger(Class clazz) {
        return CSLoggerFactory.getLogger(getLoggerName(buildClassLoggerName(clazz)));
    }

    public static CSLogger getLogger(Class clazz, String... prefixes) {
        return getLogger(buildClassLoggerName(clazz), prefixes);
    }

    public static CSLogger getLogger(String name, String... prefixes) {
        String prefix = null;
        if (prefixes != null && prefixes.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (String prefixX : prefixes) {
                if (prefixX != null) {
                    if (prefixX.equals(SPACE)) {
                        sb.append(" ");
                    } else {
                        sb.append("[").append(prefixX).append("]");
                    }
                }
            }
            if (sb.length() > 0) {
                sb.append(" ");
                prefix = sb.toString();
            }
        }
        return CSLoggerFactory.getLogger(prefix, getLoggerName(name));
    }

    private static String buildClassLoggerName(Class clazz) {
        String name = clazz.getName();
        if (name.startsWith("net.csdn.")) {
            name = Classes.getPackageName(clazz);
        }
        return name;
    }

    private static String getLoggerName(String name) {
        if (name.startsWith("net.csdn.")) {
            name = name.substring("net.csdn.".length());
        }
        return commonPrefix + name;
    }
}
