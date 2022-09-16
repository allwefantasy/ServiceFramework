package net.csdn.common.logging.log4j;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.CSLoggerFactory;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * BlogInfo: william
 * Date: 11-9-1
 * Time: 下午3:44
 */
public class Log4jFactory extends CSLoggerFactory {
    @Override
    protected CSLogger newInstance(String prefix, String name) {
        final Logger logger = LogManager.getLogger(name);
        return new Log4jCSLogger(prefix, logger);
    }


    public interface CSLogLevel {
        Level HADOO_LEVEL = Level.forName("HADOO", 550);
    }

}
