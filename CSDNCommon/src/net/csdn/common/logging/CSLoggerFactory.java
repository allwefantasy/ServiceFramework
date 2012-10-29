package net.csdn.common.logging;

import net.csdn.common.logging.log4j.Log4jFactory;

/**
 * BlogInfo: william
 * Date: 11-9-1
 * Time: 下午3:43
 */
public abstract class CSLoggerFactory {
    private static volatile CSLoggerFactory defaultFactory = new Log4jFactory();

    /**
     * Changes the default factory.
     */
    public static void setDefaultFactory(CSLoggerFactory defaultFactory) {
        if (defaultFactory == null) {
            throw new NullPointerException("defaultFactory");
        }
        CSLoggerFactory.defaultFactory = defaultFactory;
    }


    public static CSLogger getLogger(String prefix, String name) {
        return defaultFactory.newInstance(prefix == null ? null : prefix.intern(), name.intern());
    }

    public static CSLogger getLogger(String name) {
        return defaultFactory.newInstance(name.intern());
    }

    public CSLogger newInstance(String name) {
        return newInstance(null, name);
    }

    protected abstract CSLogger newInstance(String prefix, String name);

}
