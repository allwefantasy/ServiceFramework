package net.csdn.jpa.query;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Identifier checks shared by declaration validation and JPQL rendering.
 * User values never pass through these methods.
 */
final class QueryIdentifiers {

    private static final Set<String> JAVA_KEYWORDS = keywords(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null"
    );

    /**
     * Path segments that would make the limited JPQL renderer ambiguous.
     * This is narrower than the full JPQL grammar on purpose.
     */
    private static final Set<String> JPQL_RESERVED = keywords(
            "select", "from", "where", "order", "by", "and", "or", "null", "is", "not", "like", "between",
            "empty", "member", "of", "join", "group", "having", "as", "distinct", "fetch", "set", "update",
            "delete", "exists", "all", "any", "some", "true", "false", "asc", "desc", "new", "object",
            "key", "value", "entry", "index", "type", "case", "when", "then", "else", "end", "outer",
            "inner", "left", "right", "cross", "on", "with", "treat"
    );

    private QueryIdentifiers() {
    }

    static boolean isJavaIdentifier(String value) {
        if (value == null || value.length() == 0) {
            return false;
        }
        if (!Character.isJavaIdentifierStart(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (!Character.isJavaIdentifierPart(ch) || ch == '$') {
                return false;
            }
        }
        return true;
    }

    static boolean isJavaKeyword(String value) {
        return JAVA_KEYWORDS.contains(value);
    }

    static boolean isJpqlReserved(String value) {
        return value != null && JPQL_RESERVED.contains(value.toLowerCase(Locale.ENGLISH));
    }

    static boolean isEntityName(String value) {
        if (value == null || value.length() == 0 || value.length() > 300 || value.indexOf('$') >= 0) {
            return false;
        }
        String[] parts = value.split("\\.", -1);
        if (parts.length == 0) {
            return false;
        }
        for (int i = 0; i < parts.length; i++) {
            if (!isJavaIdentifier(parts[i]) || isJavaKeyword(parts[i])) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> keywords(String... values) {
        HashSet<String> set = new HashSet<String>();
        Collections.addAll(set, values);
        return Collections.unmodifiableSet(set);
    }
}
