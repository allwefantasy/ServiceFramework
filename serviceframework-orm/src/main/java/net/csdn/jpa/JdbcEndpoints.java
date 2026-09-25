package net.csdn.jpa;

/**
 * Host, port and database for framework diagnostic text.
 * Query and userinfo stay out so a jdbc option cannot print a secret.
 */
public final class JdbcEndpoints {

    private JdbcEndpoints() {
    }

    public static String endpoint(String url) {
        if (url == null || url.length() == 0) {
            return "";
        }
        String value = url;
        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int scheme = value.indexOf("://");
        if (scheme < 0) {
            return value;
        }
        String rest = value.substring(scheme + 3);
        int at = rest.lastIndexOf('@');
        if (at >= 0) {
            rest = rest.substring(at + 1);
        }
        return value.substring(0, scheme + 3) + rest;
    }
}
