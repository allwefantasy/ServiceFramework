package net.csdn.common;

import java.lang.reflect.Modifier;

/**
 * BlogInfo: william
 * Date: 11-9-1
 * Time: 上午11:39
 */
public class Classes {
    private static final char PACKAGE_SEPARATOR = '.';

    public static ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ex) {
            //cannot access thread context ClassLoader - falling back to system class loader
        }
        if (cl == null) {
            cl = Classes.class.getClassLoader();
        }
        return cl;
    }

    public static String getPackageName(Class clazz) {
        String className = clazz.getName();
        int lastDotIndex = className.lastIndexOf(PACKAGE_SEPARATOR);
        return (lastDotIndex != -1 ? className.substring(0, lastDotIndex) : "");
    }

    public static String getPackageNameNoDomain(Class clazz) {
        String fullPackage = getPackageName(clazz);
        if (fullPackage.startsWith("org.") || fullPackage.startsWith("com.") || fullPackage.startsWith("net.")) {
            return fullPackage.substring(4);
        }
        return fullPackage;
    }

    public static boolean isInnerClass(Class<?> clazz) {
        return !Modifier.isStatic(clazz.getModifiers())
                && clazz.getEnclosingClass() != null;
    }

    public static boolean isConcrete(Class<?> clazz) {
        int modifiers = clazz.getModifiers();
        return !clazz.isInterface() && !Modifier.isAbstract(modifiers);
    }

    private Classes() {

    }
}
