package net.csdn.exception;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午2:22
 */
public class SettingsException extends RuntimeException {

    public SettingsException(String message) {
        super(message);
    }

    public SettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}