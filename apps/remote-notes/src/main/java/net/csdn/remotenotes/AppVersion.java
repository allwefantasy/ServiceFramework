package net.csdn.remotenotes;

/**
 * Values returned by {@code GET /health}. Publishing a new application build
 * changes these constants; publishing the framework does not.
 */
public final class AppVersion {
    public static final String VALUE = "1.1.0";
    public static final String MARKER = "republished";

    private AppVersion() {
    }
}
