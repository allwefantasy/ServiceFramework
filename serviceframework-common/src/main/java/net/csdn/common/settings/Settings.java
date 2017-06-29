package net.csdn.common.settings;

import com.google.common.collect.ImmutableMap;
import net.csdn.common.exception.SettingsException;
import net.csdn.common.unit.ByteSizeValue;
import net.csdn.common.unit.SizeValue;
import net.csdn.common.unit.TimeValue;

import java.util.Map;

/**
 * BlogInfo: william
 * Date: 11-9-1
 * Time: 下午2:20
 */
public interface Settings {
    /**
     * A settings that are filtered (and key is removed) with the specified prefix.
     */
    Settings getByPrefix(String prefix);

    /**
     * The class loader associated with this settings.
     */
    ClassLoader getClassLoader();

    /**
     * The settings as a {@link Map}.
     */
    ImmutableMap<String, String> getAsMap();

    /**
     * Returns the setting value associated with the setting key.
     *
     * @param setting The setting key
     * @return The setting value, <tt>null</tt> if it does not exists.
     */
    String get(String setting);

    /**
     * Returns the setting value associated with the setting key. If it does not exists,
     * returns the default value provided.
     *
     * @param setting      The setting key
     * @param defaultValue The value to return if no value is associated with the setting
     * @return The setting value, or the default value if no value exists
     */
    String get(String setting, String defaultValue);

    /**
     * Returns group settings for the given setting prefix.
     */
    Map<String, Settings> getGroups(String settingPrefix) throws SettingsException;

    /**
     * Returns the setting value (as float) associated with the setting key. If it does not exists,
     * returns the default value provided.
     *
     * @param setting      The setting key
     * @param defaultValue The value to return if no value is associated with the setting
     * @return The (float) value, or the default value if no value exists.
     * @throws SettingsException Failure to parse the setting
     */
    Float getAsFloat(String setting, Float defaultValue) throws SettingsException;

    /**
     * Returns the setting value (as double) associated with the setting key. If it does not exists,
     * returns the default value provided.
     *
     * @param setting      The setting key
     * @param defaultValue The value to return if no value is associated with the setting
     * @return The (double) value, or the default value if no value exists.
     * @throws SettingsException Failure to parse the setting
     */
    Double getAsDouble(String setting, Double defaultValue) throws SettingsException;

    /**
     * Returns the setting value (as int) associated with the setting key. If it does not exists,
     * returns the default value provided.
     *
     * @param setting      The setting key
     * @param defaultValue The value to return if no value is associated with the setting
     * @return The (int) value, or the default value if no value exists.
     * @throws SettingsException Failure to parse the setting
     */
    Integer getAsInt(String setting, Integer defaultValue) throws SettingsException;

    /**
     * Returns the setting value (as long) associated with the setting key. If it does not exists,
     * returns the default value provided.
     *
     * @param setting      The setting key
     * @param defaultValue The value to return if no value is associated with the setting
     * @return The (long) value, or the default value if no value exists.
     * @throws SettingsException Failure to parse the setting
     */
    Long getAsLong(String setting, Long defaultValue) throws SettingsException;

    /**
     * Returns the setting value (as boolean) associated with the setting key. If it does not exists,
     * returns the default value provided.
     *
     * @param setting      The setting key
     * @param defaultValue The value to return if no value is associated with the setting
     * @return The (boolean) value, or the default value if no value exists.
     * @throws SettingsException Failure to parse the setting
     */
    Boolean getAsBoolean(String setting, Boolean defaultValue) throws SettingsException;

    /**
     * Returns the setting value (as time) associated with the setting key. If it does not exists,
     * returns the default value provided.
     *
     * @param setting      The setting key
     * @param defaultValue The value to return if no value is associated with the setting
     * @return The (time) value, or the default value if no value exists.
     * @throws SettingsException Failure to parse the setting
     */
    TimeValue getAsTime(String setting, TimeValue defaultValue) throws SettingsException;

    /**
     * Returns the setting value (as size) associated with the setting key. If it does not exists,
     * returns the default value provided.
     *
     * @param setting      The setting key
     * @param defaultValue The value to return if no value is associated with the setting
     * @return The (size) value, or the default value if no value exists.
     * @throws SettingsException Failure to parse the setting
     */
    ByteSizeValue getAsBytesSize(String setting, ByteSizeValue defaultValue) throws SettingsException;

    /**
     * Returns the setting value (as size) associated with the setting key. If it does not exists,
     * returns the default value provided.
     *
     * @param setting      The setting key
     * @param defaultValue The value to return if no value is associated with the setting
     * @return The (size) value, or the default value if no value exists.
     * @throws SettingsException Failure to parse the setting
     */
    SizeValue getAsSize(String setting, SizeValue defaultValue) throws SettingsException;

    /**
     * Returns the setting value (as a class) associated with the setting key. If it does not exists,
     * returns the default class provided.
     *
     * @param setting      The setting key
     * @param defaultClazz The class to return if no value is associated with the setting
     * @param <T>          The type of the class
     * @return The class setting value, or the default class provided is no value exists
     * @throws net.csdn.common.settings.NoClassSettingsException
     *          Failure to load a class
     */
    <T> Class<? extends T> getAsClass(String setting, Class<? extends T> defaultClazz) throws NoClassSettingsException;

    /**
     * Returns the setting value (as a class) associated with the setting key. If the value itself fails to
     * represent a loadable class, the value will be appended to the <tt>prefixPackage</tt> and suffixed with the
     * <tt>suffixClassName</tt> and it will try to be loaded with it.
     *
     * @param setting         The setting key
     * @param defaultClazz    The class to return if no value is associated with the setting
     * @param prefixPackage   The prefix package to prefix the value with if failing to load the class as is
     * @param suffixClassName The suffix class name to prefix the value with if failing to load the class as is
     * @param <T>             The type of the class
     * @return The class represented by the setting value, or the default class provided if no value exists
     * @throws net.csdn.common.settings.NoClassSettingsException
     *          Failure to load the class
     */
    <T> Class<? extends T> getAsClass(String setting, Class<? extends T> defaultClazz, String prefixPackage, String suffixClassName) throws NoClassSettingsException;

    /**
     * The values associated with a setting prefix as an array. The settings array is in the format of:
     * <tt>settingPrefix.[index]</tt>.
     * <p/>
     * <p>It will also automatically load a comma separated list under the settingPrefix and merge with
     * the numbered format.
     *
     * @param settingPrefix The setting prefix to load the array by
     * @return The setting array values
     * @throws SettingsException
     */
    String[] getAsArray(String settingPrefix, String[] defaultArray) throws SettingsException;

    /**
     * The values associated with a setting prefix as an array. The settings array is in the format of:
     * <tt>settingPrefix.[index]</tt>.
     * <p/>
     * <p>It will also automatically load a comma separated list under the settingPrefix and merge with
     * the numbered format.
     *
     * @param settingPrefix The setting prefix to load the array by
     * @return The setting array values
     * @throws SettingsException
     */
    String[] getAsArray(String settingPrefix) throws SettingsException;

    /**
     * A settings builder interface.
     */
    interface Builder {

        /**
         * Builds the settings.
         */
        Settings build();
    }
}
