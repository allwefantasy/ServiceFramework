package net.csdn.common.logging.log4j;

import net.csdn.common.logging.support.AbstractCSLogger;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午3:24
 */
public class Log4jCSLogger extends AbstractCSLogger {
    private final org.apache.log4j.Logger logger;

    public Log4jCSLogger(String prefix, Logger logger) {
        super(prefix);
        this.logger = logger;
    }

    @Override
    public String getName() {
        return logger.getName();
    }

    @Override
    public boolean isTraceEnabled() {
        return logger.isTraceEnabled();
    }

    @Override
    public boolean isDebugEnabled() {
        return logger.isDebugEnabled();
    }

    @Override
    public boolean isInfoEnabled() {
        return logger.isInfoEnabled();
    }

    @Override
    public boolean isWarnEnabled() {
        return logger.isEnabledFor(Level.WARN);
    }

    @Override
    public boolean isErrorEnabled() {
        return logger.isEnabledFor(Level.ERROR);
    }

    @Override
    public boolean isHadooEnabled() {
        return logger.isEnabledFor(Log4jFactory.CSLogLevel.HADOO_LEVEL);
    }

    @Override
    protected void internalHadoo(String msg) {
        logger.log(Log4jFactory.CSLogLevel.HADOO_LEVEL, msg);
    }

    @Override
    protected void internalHadoo(String msg, Throwable cause) {
        logger.log(Log4jFactory.CSLogLevel.HADOO_LEVEL, msg, cause);
    }

    @Override
    protected void internalTrace(String msg) {
        logger.trace(msg);
    }

    @Override
    protected void internalTrace(String msg, Throwable cause) {
        logger.trace(msg, cause);
    }

    @Override
    protected void internalDebug(String msg) {
        logger.debug(msg);
    }

    @Override
    protected void internalDebug(String msg, Throwable cause) {
        logger.debug(msg, cause);
    }

    @Override
    protected void internalInfo(String msg) {
        logger.info(msg);
    }

    @Override
    protected void internalInfo(String msg, Throwable cause) {
        logger.info(msg, cause);
    }

    @Override
    protected void internalWarn(String msg) {
        logger.warn(msg);
    }

    @Override
    protected void internalWarn(String msg, Throwable cause) {
        logger.warn(msg, cause);
    }

    @Override
    protected void internalError(String msg) {
        logger.error(msg);
    }

    @Override
    protected void internalError(String msg, Throwable cause) {
        logger.error(msg, cause);
    }
}
