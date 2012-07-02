package net.csdn.common.logging.support;

import net.csdn.common.logging.CSLogger;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午2:39
 */
public abstract class AbstractCSLogger implements CSLogger {
    private final String prefix;

    protected AbstractCSLogger(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public String getPrefix() {
        return this.prefix;
    }


    @Override
    public void hadoo(String msg, Object... params) {
        if (isHadooEnabled()) {
            internalHadoo(MessageFormat.format(prefix, msg, params));
        }
    }

    protected abstract void internalHadoo(String msg);

    @Override
    public void hadoo(String msg, Throwable cause, Object... params) {
        if (isTraceEnabled()) {
            internalHadoo(MessageFormat.format(prefix, msg, params), cause);
        }
    }

    protected abstract void internalHadoo(String msg, Throwable cause);


    @Override
    public void trace(String msg, Object... params) {
        if (isTraceEnabled()) {
            internalTrace(MessageFormat.format(prefix, msg, params));
        }
    }

    protected abstract void internalTrace(String msg);

    @Override
    public void trace(String msg, Throwable cause, Object... params) {
        if (isTraceEnabled()) {
            internalTrace(MessageFormat.format(prefix, msg, params), cause);
        }
    }

    protected abstract void internalTrace(String msg, Throwable cause);


    @Override
    public void debug(String msg, Object... params) {
        if (isDebugEnabled()) {
            internalDebug(MessageFormat.format(prefix, msg, params));
        }
    }

    protected abstract void internalDebug(String msg);

    @Override
    public void debug(String msg, Throwable cause, Object... params) {
        if (isDebugEnabled()) {
            internalDebug(MessageFormat.format(prefix, msg, params), cause);
        }
    }

    protected abstract void internalDebug(String msg, Throwable cause);


    @Override
    public void info(String msg, Object... params) {
        if (isInfoEnabled()) {
            internalInfo(MessageFormat.format(prefix, msg, params));
        }
    }

    protected abstract void internalInfo(String msg);

    @Override
    public void info(String msg, Throwable cause, Object... params) {
        if (isInfoEnabled()) {
            internalInfo(MessageFormat.format(prefix, msg, params), cause);
        }
    }

    protected abstract void internalInfo(String msg, Throwable cause);


    @Override
    public void warn(String msg, Object... params) {
        if (isWarnEnabled()) {
            internalWarn(MessageFormat.format(prefix, msg, params));
        }
    }

    protected abstract void internalWarn(String msg);

    @Override
    public void warn(String msg, Throwable cause, Object... params) {
        if (isWarnEnabled()) {
            internalWarn(MessageFormat.format(prefix, msg, params), cause);
        }
    }

    protected abstract void internalWarn(String msg, Throwable cause);


    @Override
    public void error(String msg, Object... params) {
        if (isErrorEnabled()) {
            internalError(MessageFormat.format(prefix, msg, params));
        }
    }

    protected abstract void internalError(String msg);

    @Override
    public void error(String msg, Throwable cause, Object... params) {
        if (isErrorEnabled()) {
            internalError(MessageFormat.format(prefix, msg, params), cause);
        }
    }

    protected abstract void internalError(String msg, Throwable cause);
}
