package net.csdn.modules.log;

import com.google.inject.Inject;
import net.csdn.common.env.Environment;
import net.csdn.common.settings.Settings;
import org.apache.log4j.*;

import java.io.IOException;

/**
 * 10/29/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class SystemLoggerImpl implements SystemLogger {


    private Settings settings;
    private Logger traceLogger;

    @Inject
    public SystemLoggerImpl(Settings settings) {
        this.settings = settings;
        this.traceLogger = createLogger("tracer");
    }


    private Logger createLogger(String name) {
        Logger logger = Logger.getLogger(name);
        Layout layout = new PatternLayout("%m%n");
        Appender appender = null;
        try {
            Environment environment = new Environment(settings);
            appender = new DailyRollingFileAppender(layout, environment.logsFile().getPath() + "/" + name + "/" + name, "'.'yyyy-MM-dd");
        } catch (IOException e) {
            e.printStackTrace();
        }
        logger.addAppender(appender);
        return logger;
    }

    @Override
    public Logger traceLogger() {
        return traceLogger;
    }
}
