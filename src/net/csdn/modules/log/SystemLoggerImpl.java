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

    private Logger applicationLogger;
    private Logger behaviorLogger;
    private Logger healthLogger;
    private Settings settings;

    @Inject
    public SystemLoggerImpl(Settings settings) {
        this.settings = settings;
        this.applicationLogger = createLogger("sapplication");
        this.behaviorLogger = createLogger("sbehavior");
        this.healthLogger = createLogger("shealth");
    }

    @Override
    public Logger applicationLogger() {
        return applicationLogger;
    }

    @Override
    public Logger behaviorLogger() {
        return behaviorLogger;
    }

    @Override
    public Logger healthLogger() {
        return healthLogger;
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
}
