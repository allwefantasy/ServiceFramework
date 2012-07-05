package net.csdn.common.settings;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import net.csdn.common.Booleans;
import net.csdn.common.Classes;
import net.csdn.common.Strings;
import net.csdn.common.io.Streams;
import net.csdn.common.property.PropertyPlaceholder;
import net.csdn.common.unit.ByteSizeUnit;
import net.csdn.common.unit.ByteSizeValue;
import net.csdn.common.unit.SizeValue;
import net.csdn.common.unit.TimeValue;
import net.csdn.exception.SettingsException;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Maps.newHashMap;
import static net.csdn.common.Strings.toCamelCase;
import static net.csdn.common.unit.ByteSizeValue.parseBytesSizeValue;
import static net.csdn.common.unit.SizeValue.parseSizeValue;
import static net.csdn.common.unit.TimeValue.parseTimeValue;

/**
 * BlogInfo: william
 * Date: 11-9-1
 * Time: 下午3:56
 */
public class ImmutableSettings implements Settings {

    private ImmutableMap<String, String> settings;

    private transient ClassLoader classLoader;

    private ImmutableSettings(Map<String, String> settings, ClassLoader classLoader) {
        this.settings = ImmutableMap.copyOf(settings);
        this.classLoader = classLoader == null ? buildClassLoader() : classLoader;
    }

    @Override
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    @Override
    public ImmutableMap<String, String> getAsMap() {
        return this.settings;
    }


    @Override
    public Settings getByPrefix(String prefix) {
        Builder builder = new Builder();
        for (Map.Entry<String, String> entry : getAsMap().entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                if (entry.getKey().length() < prefix.length()) {
                    // ignore this one
                    continue;
                }
                builder.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        builder.classLoader(classLoader);
        return builder.build();
    }

    @Override
    public String get(String setting) {
        String retVal = settings.get(setting);
        if (retVal != null) {
            return retVal;
        }
        // try camel case version
        return settings.get(toCamelCase(setting));
    }

    @Override
    public String get(String setting, String defaultValue) {
        String retVal = settings.get(setting);
        return retVal == null ? defaultValue : retVal;
    }

    @Override
    public Float getAsFloat(String setting, Float defaultValue) {
        String sValue = get(setting);
        if (sValue == null) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(sValue);
        } catch (NumberFormatException e) {
            throw new SettingsException("Failed to parse float setting [" + setting + "] with value [" + sValue + "]", e);
        }
    }

    @Override
    public Double getAsDouble(String setting, Double defaultValue) {
        String sValue = get(setting);
        if (sValue == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(sValue);
        } catch (NumberFormatException e) {
            throw new SettingsException("Failed to parse double setting [" + setting + "] with value [" + sValue + "]", e);
        }
    }

    @Override
    public Integer getAsInt(String setting, Integer defaultValue) {
        String sValue = get(setting);
        if (sValue == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(sValue);
        } catch (NumberFormatException e) {
            throw new SettingsException("Failed to parse int setting [" + setting + "] with value [" + sValue + "]", e);
        }
    }

    @Override
    public Long getAsLong(String setting, Long defaultValue) {
        String sValue = get(setting);
        if (sValue == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(sValue);
        } catch (NumberFormatException e) {
            throw new SettingsException("Failed to parse long setting [" + setting + "] with value [" + sValue + "]", e);
        }
    }

    @Override
    public Boolean getAsBoolean(String setting, Boolean defaultValue) {
        return Booleans.parseBoolean(get(setting), defaultValue);
    }

    public TimeValue getAsTime(String setting, TimeValue defaultValue) {
        return parseTimeValue(get(setting), defaultValue);
    }

    public ByteSizeValue getAsBytesSize(String setting, ByteSizeValue defaultValue) throws SettingsException {
        return parseBytesSizeValue(get(setting), defaultValue);
    }

    public SizeValue getAsSize(String setting, SizeValue defaultValue) throws SettingsException {
        return parseSizeValue(get(setting), defaultValue);
    }


    @SuppressWarnings({"unchecked"})
    @Override
    public <T> Class<? extends T> getAsClass(String setting, Class<? extends T> defaultClazz) throws NoClassSettingsException {
        String sValue = get(setting);
        if (sValue == null) {
            return defaultClazz;
        }
        try {
            return (Class<? extends T>) getClassLoader().loadClass(sValue);
        } catch (ClassNotFoundException e) {
            throw new NoClassSettingsException("Failed to load class setting [" + setting + "] with value [" + sValue + "]", e);
        }
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <T> Class<? extends T> getAsClass(String setting, Class<? extends T> defaultClazz, String prefixPackage, String suffixClassName) throws NoClassSettingsException {
        String sValue = get(setting);
        if (sValue == null) {
            return defaultClazz;
        }
        String fullClassName = sValue;
        try {
            return (Class<? extends T>) getClassLoader().loadClass(fullClassName);
        } catch (ClassNotFoundException e) {
            fullClassName = prefixPackage + Strings.capitalize(toCamelCase(sValue)) + suffixClassName;
            try {
                return (Class<? extends T>) getClassLoader().loadClass(fullClassName);
            } catch (ClassNotFoundException e1) {
                fullClassName = prefixPackage + toCamelCase(sValue).toLowerCase() + "." + Strings.capitalize(toCamelCase(sValue)) + suffixClassName;
                try {
                    return (Class<? extends T>) getClassLoader().loadClass(fullClassName);
                } catch (ClassNotFoundException e2) {
                    throw new NoClassSettingsException("Failed to load class setting [" + setting + "] with value [" + sValue + "]", e);
                }
            }
        }
    }

    @Override
    public String[] getAsArray(String settingPrefix) throws SettingsException {
        return getAsArray(settingPrefix, Strings.EMPTY_ARRAY);
    }

    @Override
    public String[] getAsArray(String settingPrefix, String[] defaultArray) throws SettingsException {
        List<String> result = Lists.newArrayList();

        if (get(settingPrefix) != null) {
            Collections.addAll(result, Strings.commaDelimitedListToStringArray(get(settingPrefix)));
        }

        int counter = 0;
        while (true) {
            String value = get(settingPrefix + '.' + (counter++));
            if (value == null) {
                break;
            }
            result.add(value);
        }
        if (result.isEmpty()) {
            return defaultArray;
        }
        return result.toArray(new String[result.size()]);
    }

    @Override
    public Map<String, Settings> getGroups(String settingPrefix) throws SettingsException {
        if (settingPrefix.charAt(settingPrefix.length() - 1) != '.') {
            settingPrefix = settingPrefix + ".";
        }
        // we don't really care that it might happen twice
        Map<String, Map<String, String>> map = new LinkedHashMap<String, Map<String, String>>();
        for (Object o : settings.keySet()) {
            String setting = (String) o;
            if (setting.startsWith(settingPrefix)) {
                String nameValue = setting.substring(settingPrefix.length());
                int dotIndex = nameValue.indexOf('.');
                if (dotIndex == -1) {
                    throw new SettingsException("Failed to get setting group for [" + settingPrefix + "] setting prefix and setting [" + setting + "] because of a missing '.'");
                }
                String name = nameValue.substring(0, dotIndex);
                String value = nameValue.substring(dotIndex + 1);
                Map<String, String> groupSettings = map.get(name);
                if (groupSettings == null) {
                    groupSettings = new LinkedHashMap<String, String>();
                    map.put(name, groupSettings);
                }
                groupSettings.put(value, get(setting));
            }
        }
        Map<String, Settings> retVal = new LinkedHashMap<String, Settings>();
        for (Map.Entry<String, Map<String, String>> entry : map.entrySet()) {
            retVal.put(entry.getKey(), new ImmutableSettings(Collections.unmodifiableMap(entry.getValue()), classLoader));
        }
        return Collections.unmodifiableMap(retVal);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || this.getClass() != o.getClass()) return false;

        ImmutableSettings that = (ImmutableSettings) o;

        if (classLoader != null ? !classLoader.equals(that.classLoader) : that.classLoader != null) return false;
        if (settings != null ? !settings.equals(that.settings) : that.settings != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = settings != null ? settings.hashCode() : 0;
        result = 31 * result + (classLoader != null ? classLoader.hashCode() : 0);
        return result;
    }

    private static ClassLoader buildClassLoader() {
        return Classes.getDefaultClassLoader();
    }


    /**
     * Returns a builder to be used in order to build settings.
     */
    public static Builder settingsBuilder() {
        return new Builder();
    }

    /**
     * A builder allowing to put different settings and then {@link #build()} an immutable
     * settings implementation. Use {@link ImmutableSettings#settingsBuilder()} in order to
     * construct it.
     */
    public static class Builder implements Settings.Builder {

        public static final Settings EMPTY_SETTINGS = new Builder().build();

        private final Map<String, String> map = new LinkedHashMap<String, String>();

        private ClassLoader classLoader;

        private Builder() {

        }

        public Map<String, String> internalMap() {
            return this.map;
        }

        /**
         * Removes the provided setting.
         */
        public String remove(String key) {
            return map.remove(key);
        }

        /**
         * Returns a setting value based on the setting key.
         */
        public String get(String key) {
            String retVal = map.get(key);
            if (retVal != null) {
                return retVal;
            }
            // try camel case version
            return map.get(toCamelCase(key));
        }

        /**
         * Sets a setting with the provided setting key and value.
         *
         * @param key   The setting key
         * @param value The setting value
         * @return The builder
         */
        public Builder put(String key, String value) {
            map.put(key, value);
            return this;
        }

        /**
         * Sets a setting with the provided setting key and class as value.
         *
         * @param key   The setting key
         * @param clazz The setting class value
         * @return The builder
         */
        public Builder put(String key, Class clazz) {
            map.put(key, clazz.getName());
            return this;
        }

        /**
         * Sets the setting with the provided setting key and the boolean value.
         *
         * @param setting The setting key
         * @param value   The boolean value
         * @return The builder
         */
        public Builder put(String setting, boolean value) {
            put(setting, String.valueOf(value));
            return this;
        }

        /**
         * Sets the setting with the provided setting key and the int value.
         *
         * @param setting The setting key
         * @param value   The int value
         * @return The builder
         */
        public Builder put(String setting, int value) {
            put(setting, String.valueOf(value));
            return this;
        }

        /**
         * Sets the setting with the provided setting key and the long value.
         *
         * @param setting The setting key
         * @param value   The long value
         * @return The builder
         */
        public Builder put(String setting, long value) {
            put(setting, String.valueOf(value));
            return this;
        }

        /**
         * Sets the setting with the provided setting key and the float value.
         *
         * @param setting The setting key
         * @param value   The float value
         * @return The builder
         */
        public Builder put(String setting, float value) {
            put(setting, String.valueOf(value));
            return this;
        }

        /**
         * Sets the setting with the provided setting key and the double value.
         *
         * @param setting The setting key
         * @param value   The double value
         * @return The builder
         */
        public Builder put(String setting, double value) {
            put(setting, String.valueOf(value));
            return this;
        }

        /**
         * Sets the setting with the provided setting key and the time value.
         *
         * @param setting The setting key
         * @param value   The time value
         * @return The builder
         */
        public Builder put(String setting, long value, TimeUnit timeUnit) {
            put(setting, timeUnit.toMillis(value));
            return this;
        }

        /**
         * Sets the setting with the provided setting key and the size value.
         *
         * @param setting The setting key
         * @param value   The size value
         * @return The builder
         */
        public Builder put(String setting, long value, ByteSizeUnit sizeUnit) {
            put(setting, sizeUnit.toBytes(value));
            return this;
        }

        /**
         * Sets the setting with the provided setting key and an array of values.
         *
         * @param setting The setting key
         * @param values  The values
         * @return The builder
         */
        public Builder putArray(String setting, String... values) {
            int counter = 0;
            while (true) {
                String value = map.remove(setting + '.' + (counter++));
                if (value == null) {
                    break;
                }
            }
            for (int i = 0; i < values.length; i++) {
                put(setting + "." + i, values[i]);
            }
            return this;
        }

        /**
         * Sets the setting group.
         */
        public Builder put(String settingPrefix, String groupName, String[] settings, String[] values) throws SettingsException {
            if (settings.length != values.length) {
                throw new SettingsException("The settings length must match the value length");
            }
            for (int i = 0; i < settings.length; i++) {
                if (values[i] == null) {
                    continue;
                }
                put(settingPrefix + "." + groupName + "." + settings[i], values[i]);
            }
            return this;
        }

        /**
         * Sets all the provided settings.
         */
        public Builder put(Settings settings) {
            map.putAll(settings.getAsMap());
            return this;
        }

        /**
         * Sets all the provided settings.
         */
        public Builder put(Map<String, String> settings) {
            map.putAll(settings);
            return this;
        }

        /**
         * Sets all the provided settings.
         */
        public Builder put(Properties properties) {
            for (Map.Entry entry : properties.entrySet()) {
                map.put((String) entry.getKey(), (String) entry.getValue());
            }
            return this;
        }

        /**
         * Loads settings from the actual string write that represents them using the
         */
        public Builder loadFromSource(String source) {

            try {
                Map<String, String> loadedSettings = YamlSettingsLoader.load(source);
                put(loadedSettings);
            } catch (Exception e) {
                throw new SettingsException("Failed to load settings from [" + source + "]");
            }
            return this;
        }

        /**
         * Loads settings from a url that represents them using the
         */
        public Builder loadFromUrl(URL url) throws SettingsException {
            try {
                return loadFromStream(url.toExternalForm(), url.openStream());
            } catch (IOException e) {
                throw new SettingsException("Failed to open stream for url [" + url.toExternalForm() + "]", e);
            }
        }

        /**
         * Loads settings from a stream that represents them using the
         */
        public Builder loadFromStream(String resourceName, InputStream is) throws SettingsException {
            try {
                Map<String, String> loadedSettings = YamlSettingsLoader.load(Streams.copyToString(new InputStreamReader(is, "UTF-8")));
                put(loadedSettings);
            } catch (Exception e) {
                throw new SettingsException("Failed to load settings from [" + resourceName + "]", e);
            }
            return this;
        }

        /**
         * Loads settings from classpath that represents them using the
         */
        public Builder loadFromClasspath(String resourceName) throws SettingsException {
            ClassLoader classLoader = this.classLoader;
            if (classLoader == null) {
                classLoader = buildClassLoader();
            }
            InputStream is = classLoader.getResourceAsStream(resourceName);
            if (is == null) {
                return this;
            }

            return loadFromStream(resourceName, is);
        }

        /**
         * Sets the class loader associated with the settings built.
         */
        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        /**
         * Runs across all the settings set on this builder and replaces <tt>${...}</tt> elements in the
         * each setting value according to the following logic:
         * <p/>
         * <p>First, tries to resolve it against a System property ({@link System#getProperty(String)}), next,
         * tries and resolve it against an environment variable ({@link System#getenv(String)}), and last, tries
         * and replace it with another setting already set on this builder.
         */
        public Builder replacePropertyPlaceholders() {
            PropertyPlaceholder propertyPlaceholder = new PropertyPlaceholder("${", "}", false);
            PropertyPlaceholder.PlaceholderResolver placeholderResolver = new PropertyPlaceholder.PlaceholderResolver() {
                @Override
                public String resolvePlaceholder(String placeholderName) {
                    String value = System.getProperty(placeholderName);
                    if (value != null) {
                        return value;
                    }
                    value = System.getenv(placeholderName);
                    if (value != null) {
                        return value;
                    }
                    return map.get(placeholderName);
                }
            };
            for (Map.Entry<String, String> entry : map.entrySet()) {
                map.put(entry.getKey(), propertyPlaceholder.replacePlaceholders(entry.getValue(), placeholderResolver));
            }
            return this;
        }

        /**
         * Puts all the properties with keys starting with the provided <tt>prefix</tt>.
         *
         * @param prefix     The prefix to filter proeprty key by
         * @param properties The properties to put
         * @return The builder
         */
        public Builder putProperties(String prefix, Properties properties) {
            for (Object key1 : properties.keySet()) {
                String key = (String) key1;
                String value = properties.getProperty(key);
                if (key.startsWith(prefix)) {
                    map.put(key.substring(prefix.length()), value);
                }
            }
            return this;
        }


        /**
         * Builds a {@link Settings} (underlying uses {@link ImmutableSettings}) based on everything
         * set on this builder.
         */
        public Settings build() {
            return new ImmutableSettings(Collections.unmodifiableMap(map), classLoader);
        }
    }

    public static class YamlSettingsLoader {

        public static Map<String, String> load(String source) throws IOException {
            // replace tabs with whitespace (yaml does not accept tabs, but many users might use it still...)
            source = source.replace("\t", "  ");
            Yaml yaml = new Yaml();
            Map<Object, Object> yamlMap = (Map<Object, Object>) yaml.load(source);
            StringBuilder sb = new StringBuilder();
            Map<String, String> settings = newHashMap();
            if (yamlMap == null) {
                return settings;
            }
            List<String> path = newArrayList();
            serializeMap(settings, sb, path, yamlMap);
            return settings;
        }

        public static Map<String, String> load(byte[] source) throws IOException {
            Yaml yaml = new Yaml();
            Map<Object, Object> yamlMap = (Map<Object, Object>) yaml.load(source.toString());
            StringBuilder sb = new StringBuilder();
            Map<String, String> settings = newHashMap();
            if (yamlMap == null) {
                return settings;
            }
            List<String> path = newArrayList();
            serializeMap(settings, sb, path, yamlMap);
            return settings;
        }

        private static void serializeMap(Map<String, String> settings, StringBuilder sb, List<String> path, Map<Object, Object> yamlMap) {
            for (Map.Entry<Object, Object> entry : yamlMap.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    path.add((String) entry.getKey());
                    serializeMap(settings, sb, path, (Map<Object, Object>) entry.getValue());
                    path.remove(path.size() - 1);
                } else if (entry.getValue() instanceof List) {
                    path.add((String) entry.getKey());
                    serializeList(settings, sb, path, (List) entry.getValue());
                    path.remove(path.size() - 1);
                } else {
                    serializeValue(settings, sb, path, (String) entry.getKey(), entry.getValue());
                }
            }
        }

        private static void serializeList(Map<String, String> settings, StringBuilder sb, List<String> path, List yamlList) {
            int counter = 0;
            for (Object listEle : yamlList) {
                if (listEle instanceof Map) {
                    path.add(Integer.toString(counter));
                    serializeMap(settings, sb, path, (Map<Object, Object>) listEle);
                    path.remove(path.size() - 1);
                } else if (listEle instanceof List) {
                    path.add(Integer.toString(counter));
                    serializeList(settings, sb, path, (List) listEle);
                    path.remove(path.size() - 1);
                } else {
                    serializeValue(settings, sb, path, Integer.toString(counter), listEle);
                }
                counter++;
            }
        }

        private static void serializeValue(Map<String, String> settings, StringBuilder sb, List<String> path, String name, Object value) {
            if (value == null) {
                return;
            }
            sb.setLength(0);
            for (String pathEle : path) {
                sb.append(pathEle).append('.');
            }
            sb.append(name);
            settings.put(sb.toString(), value.toString());
        }
    }
}

