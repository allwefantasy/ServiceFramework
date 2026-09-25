package net.csdn.common.enhancer.fixture;

/**
 * Conventional same-package anchor. {@code token()} is package-private on purpose.
 */
public class ServiceFrameworkPackageAnchor {

    static int token() {
        return 42;
    }

    private ServiceFrameworkPackageAnchor() {
    }
}
