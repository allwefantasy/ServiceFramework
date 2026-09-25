package net.csdn.bootstrap;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Non-secret marker recorded around controller enhancement. The version is a
 * fixed token. The digest is the SHA-256 of the sorted controller class names,
 * not a settings or schema dump.
 */
public final class WebEnhancementMetadata {

    /**
     * Controller class-name digest format. Not an application configuration revision.
     */
    public static final String VERSION = "web-1";

    /**
     * Revision {@link net.csdn.bootstrap.Bootstrap} stores when diagnostics are
     * enabled and the caller has not set one. Module events keep this token.
     * {@link #VERSION} is used only when no revision is present.
     */
    public static final String APPLICATION_REVISION = "app-1";

    private WebEnhancementMetadata() {
    }

    public static String controllerDigest(List<String> classNames) {
        if (classNames == null || classNames.isEmpty()) {
            return null;
        }
        List<String> sorted = new ArrayList<String>(classNames);
        Collections.sort(sorted);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(sorted.get(i));
        }
        return sha256(builder.toString());
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes("UTF-8"));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (int i = 0; i < hash.length; i++) {
                int value = hash[i] & 0xff;
                if (value < 16) {
                    builder.append('0');
                }
                builder.append(Integer.toHexString(value));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }
}
