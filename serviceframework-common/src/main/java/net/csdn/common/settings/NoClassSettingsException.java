package net.csdn.common.settings;

import net.csdn.common.exception.SettingsException;

/**
 * BlogInfo: william
 * Date: 11-9-1
 * Time: 下午4:37
 */
public class NoClassSettingsException extends SettingsException {

    public NoClassSettingsException(String message) {
        super(message);
    }

    public NoClassSettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}
