package net.csdn.common.property;

import com.google.common.base.Preconditions;
import net.csdn.common.Strings;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * BlogInfo: william
 * Date: 11-9-2
 * Time: 上午11:32
 */
public class PropertyPlaceholder {

    private final String placeholderPrefix;

    private final String placeholderSuffix;

    private final boolean ignoreUnresolvablePlaceholders;

    /**
     * Creates a new <code>PropertyPlaceholderHelper</code> that uses the supplied prefix and suffix. Unresolvable
     * placeholders are ignored.
     *
     * @param placeholderPrefix the prefix that denotes the start of a placeholder.
     * @param placeholderSuffix the suffix that denotes the end of a placeholder.
     */
    public PropertyPlaceholder(String placeholderPrefix, String placeholderSuffix) {
        this(placeholderPrefix, placeholderSuffix, true);
    }

    /**
     * Creates a new <code>PropertyPlaceholderHelper</code> that uses the supplied prefix and suffix.
     *
     * @param placeholderPrefix              the prefix that denotes the start of a placeholder.
     * @param placeholderSuffix              the suffix that denotes the end of a placeholder.
     * @param ignoreUnresolvablePlaceholders indicates whether unresolvable placeholders should be ignored
     *                                       (<code>true</code>) or cause an exception (<code>false</code>).
     */
    public PropertyPlaceholder(String placeholderPrefix, String placeholderSuffix,
                               boolean ignoreUnresolvablePlaceholders) {
        Preconditions.checkNotNull(placeholderPrefix, "Argument 'placeholderPrefix' must not be null.");
        Preconditions.checkNotNull(placeholderSuffix, "Argument 'placeholderSuffix' must not be null.");
        this.placeholderPrefix = placeholderPrefix;
        this.placeholderSuffix = placeholderSuffix;
        this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
    }

    /**
     * Replaces all placeholders of format <code>${name}</code> with the corresponding property from the supplied {@link
     * java.util.Properties}.
     *
     * @param value      the value containing the placeholders to be replaced.
     * @param properties the <code>Properties</code> to use for replacement.
     * @return the supplied value with placeholders replaced inline.
     */
    public String replacePlaceholders(String value, final Properties properties) {
        Preconditions.checkNotNull(properties, "Argument 'properties' must not be null.");
        return replacePlaceholders(value, new PlaceholderResolver() {

            public String resolvePlaceholder(String placeholderName) {
                return properties.getProperty(placeholderName);
            }
        });
    }

    /**
     * Replaces all placeholders of format <code>${name}</code> with the value returned from the supplied {@link
     * PlaceholderResolver}.
     *
     * @param value               the value containing the placeholders to be replaced.
     * @param placeholderResolver the <code>PlaceholderResolver</code> to use for replacement.
     * @return the supplied value with placeholders replaced inline.
     */
    public String replacePlaceholders(String value, PlaceholderResolver placeholderResolver) {
        Preconditions.checkNotNull(value, "Argument 'value' must not be null.");
        return parseStringValue(value, placeholderResolver, new HashSet<String>());
    }

    protected String parseStringValue(String strVal, PlaceholderResolver placeholderResolver,
                                      Set<String> visitedPlaceholders) {
        StringBuilder buf = new StringBuilder(strVal);

        int startIndex = strVal.indexOf(this.placeholderPrefix);
        while (startIndex != -1) {
            int endIndex = findPlaceholderEndIndex(buf, startIndex);
            if (endIndex != -1) {
                String placeholder = buf.substring(startIndex + this.placeholderPrefix.length(), endIndex);
                if (!visitedPlaceholders.add(placeholder)) {
                    throw new IllegalArgumentException(
                            "Circular placeholder reference '" + placeholder + "' in property definitions");
                }
                // Recursive invocation, parsing placeholders contained in the placeholder key.
                placeholder = parseStringValue(placeholder, placeholderResolver, visitedPlaceholders);

                // Now obtain the value for the fully resolved key...
                int defaultValueIdx = placeholder.indexOf(':');
                String defaultValue = null;
                if (defaultValueIdx != -1) {
                    defaultValue = placeholder.substring(defaultValueIdx + 1);
                    placeholder = placeholder.substring(0, defaultValueIdx);
                }
                String propVal = placeholderResolver.resolvePlaceholder(placeholder);
                if (propVal == null) {
                    propVal = defaultValue;
                }
                if (propVal != null) {
                    // Recursive invocation, parsing placeholders contained in the
                    // previously resolved placeholder value.
                    propVal = parseStringValue(propVal, placeholderResolver, visitedPlaceholders);
                    buf.replace(startIndex, endIndex + this.placeholderSuffix.length(), propVal);
                    startIndex = buf.indexOf(this.placeholderPrefix, startIndex + propVal.length());
                } else if (this.ignoreUnresolvablePlaceholders) {
                    // Proceed with unprocessed value.
                    startIndex = buf.indexOf(this.placeholderPrefix, endIndex + this.placeholderSuffix.length());
                } else {
                    throw new IllegalArgumentException("Could not resolve placeholder '" + placeholder + "'");
                }

                visitedPlaceholders.remove(placeholder);
            } else {
                startIndex = -1;
            }
        }

        return buf.toString();
    }

    private int findPlaceholderEndIndex(CharSequence buf, int startIndex) {
        int index = startIndex + this.placeholderPrefix.length();
        int withinNestedPlaceholder = 0;
        while (index < buf.length()) {
            if (Strings.substringMatch(buf, index, this.placeholderSuffix)) {
                if (withinNestedPlaceholder > 0) {
                    withinNestedPlaceholder--;
                    index = index + this.placeholderPrefix.length() - 1;
                } else {
                    return index;
                }
            } else if (Strings.substringMatch(buf, index, this.placeholderPrefix)) {
                withinNestedPlaceholder++;
                index = index + this.placeholderPrefix.length();
            } else {
                index++;
            }
        }
        return -1;
    }

    /**
     * Strategy interface used to resolve replacement values for placeholders contained in Strings.
     *
     * @see PropertyPlaceholder
     */
    public static interface PlaceholderResolver {

        /**
         * Resolves the supplied placeholder name into the replacement value.
         *
         * @param placeholderName the name of the placeholder to resolve.
         * @return the replacement value or <code>null</code> if no replacement is to be made.
         */
        String resolvePlaceholder(String placeholderName);
    }
}
