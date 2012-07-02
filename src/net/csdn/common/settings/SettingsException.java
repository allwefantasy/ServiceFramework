package net.csdn.common.settings;

import net.csdn.CsdnSearchException;

/**
 * User: william
 * Date: 11-9-1
 * Time: 下午2:22
 */
public class SettingsException extends CsdnSearchException {

    public SettingsException(String message) {
        super(message);
    }

    public SettingsException(String message, Throwable cause) {
        super(message, cause);
    }
}