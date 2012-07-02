package net.csdn.common.logging.log4j;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.CSLoggerFactory;
import org.apache.log4j.Level;
import org.apache.log4j.Priority;
import org.apache.log4j.net.SyslogAppender;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午3:44
 */
public class Log4jFactory extends CSLoggerFactory {
    @Override
    protected CSLogger newInstance(String prefix, String name) {
        final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(name);
        return new Log4jCSLogger(prefix, logger);
    }

    private static class HADOOLevel extends Level {

        protected HADOOLevel(int level, String levelStr, int syslogEquivalent) {
            super(level, levelStr, syslogEquivalent);
        }
    }

    public interface CSLogLevel {
        public static final Level HADOO_LEVEL = new HADOOLevel(Priority.DEBUG_INT - 1, "HADOO", SyslogAppender.LOG_LOCAL0);
    }

}
