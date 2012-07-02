package net.csdn.common.logging;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午2:38
 */
public interface CSLogger {
    String getPrefix();

    String getName();

    /**
     * Returns {@code true} if a TRACE level message is logged.
     */
    boolean isTraceEnabled();

    /**
     * Returns {@code true} if a DEBUG level message is logged.
     */
    boolean isDebugEnabled();

    /**
     * Returns {@code true} if an INFO level message is logged.
     */
    boolean isInfoEnabled();

    /**
     * Returns {@code true} if a WARN level message is logged.
     */
    boolean isWarnEnabled();

    /**
     * Returns {@code true} if an ERROR level message is logged.
     */
    boolean isErrorEnabled();

    boolean isHadooEnabled();


    void hadoo(String msg, Object... params);

    void hadoo(String msg, Throwable cause, Object... params);

    /**
     * Logs a DEBUG level message.
     */
    void trace(String msg, Object... params);

    /**
     * Logs a DEBUG level message.
     */
    void trace(String msg, Throwable cause, Object... params);

    /**
     * Logs a DEBUG level message.
     */
    void debug(String msg, Object... params);

    /**
     * Logs a DEBUG level message.
     */
    void debug(String msg, Throwable cause, Object... params);

    /**
     * Logs an INFO level message.
     */
    void info(String msg, Object... params);

    /**
     * Logs an INFO level message.
     */
    void info(String msg, Throwable cause, Object... params);

    /**
     * Logs a WARN level message.
     */
    void warn(String msg, Object... params);

    /**
     * Logs a WARN level message.
     */
    void warn(String msg, Throwable cause, Object... params);

    /**
     * Logs an ERROR level message.
     */
    void error(String msg, Object... params);

    /**
     * Logs an ERROR level message.
     */
    void error(String msg, Throwable cause, Object... params);
}
