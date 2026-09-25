package net.csdn.common.enhancer;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.bytecode.ClassFile;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Defines one finished {@link CtClass} into an explicit loader.
 * <p>
 * JDK 8 uses {@link CtClass#toClass(ClassLoader, ProtectionDomain)}. That path is
 * compiled against Javassist's pre-module helper and this class does not mention
 * JDK 9 types. JDK 9+ uses Javassist's already compiled same-package neighbor
 * overload. Named modules are rejected. An ordinary classpath class is an unnamed
 * module and is accepted. No temporary anchor class is generated and this class
 * never requests {@code --add-opens}.
 * <p>
 * Runtime-created classes are forced to major version 52. Bytecode loaded from an
 * existing resource with a major version above 52 is rejected before definition.
 */
public final class ClassDefiner {

    public static final String ANCHOR_SIMPLE_NAME = "ServiceFrameworkPackageAnchor";
    public static final int JAVA8_MAJOR = ClassFile.JAVA_8;

    private final EnhancementContext context;
    private final Map<AnchorKey, Class<?>> anchors = new IdentityAnchorMap();
    private final Map<ClassLoader, Set<String>> definedByLoader = new IdentityHashMap<ClassLoader, Set<String>>();
    private final Set<String> bootstrapDefined = new HashSet<String>();
    private boolean closed;

    ClassDefiner(EnhancementContext context) {
        this.context = context;
    }

    public static boolean isNamedModule(Class<?> type) {
        if (type == null) {
            return false;
        }
        try {
            Method getModule = Class.class.getMethod("getModule");
            Object module = getModule.invoke(type);
            if (module == null) {
                return false;
            }
            Method isNamed = module.getClass().getMethod("isNamed");
            return Boolean.TRUE.equals(isNamed.invoke(module));
        } catch (NoSuchMethodException e) {
            return false;
        } catch (IllegalAccessException e) {
            throw moduleFailure(type, e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            throw moduleFailure(type, cause instanceof Exception ? (Exception) cause : e);
        }
    }

    public void registerAnchor(Class<?> anchor) {
        context.ensureOpen();
        ensureThisOpen();
        if (anchor == null || anchor.isPrimitive() || anchor.isArray()) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    anchor == null ? null : anchor.getName(),
                    null,
                    "register-anchor",
                    "anchor must be a real class",
                    null);
        }
        rejectNamedModule(anchor, "register-anchor");
        AnchorKey key = new AnchorKey(packageName(anchor), anchor.getClassLoader());
        Class<?> existing = anchors.get(key);
        if (existing == null) {
            anchors.put(key, anchor);
            return;
        }
        if (existing == anchor) {
            return;
        }
        throw new EnhancementFailure(
                EnhancementFailure.Category.CONFLICT,
                anchor.getName(),
                null,
                "register-anchor",
                "package " + key.packageName + " already has anchor " + existing.getName()
                        + " in this loader",
                null);
    }

    Class<?> lookupAnchor(String packageName, ClassLoader loader) {
        Class<?> registered = anchors.get(new AnchorKey(packageName, loader));
        if (registered != null) {
            return registered;
        }
        try {
            return Class.forName(anchorClassName(packageName), false, loader);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public Class<?> define(CtClass type, ClassLoader loader, ProtectionDomain domain) {
        context.ensureOpen();
        ensureThisOpen();
        long started = System.nanoTime();
        if (type == null) {
            EnhancementFailure failure = new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    null,
                    null,
                    "define",
                    "class is required; " + locate(loader, null, domain),
                    null);
            context.diagnostics().recordFailure(null, null, "define", failure, System.nanoTime() - started);
            throw failure;
        }
        String className = type.getName();
        type.stopPruning(true);
        Class<?> anchor;
        try {
            if (alreadyDefined(loader, className)) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.CONFLICT,
                        className,
                        null,
                        "define",
                        "class was already defined by this context into the target loader; "
                                + locate(loader, null, domain),
                        null);
            }
            enforceVersion(type, loader, domain);
            anchor = resolveAnchor(packageName(type), loader, domain, className);
        } catch (EnhancementFailure failure) {
            context.diagnostics().recordFailure(className, null, "define", failure, System.nanoTime() - started);
            throw failure;
        }
        byte[] resultBytes = null;
        if (context.diagnostics().enabled()) {
            context.diagnostics().noteBytecodeRead();
            try {
                type.stopPruning(true);
                resultBytes = type.toBytecode();
                if (type.isFrozen()) {
                    type.defrost();
                }
            } catch (Exception e) {
                EnhancementFailure failure = new EnhancementFailure(
                        EnhancementFailure.Category.DEFINITION,
                        className,
                        null,
                        "define",
                        "cannot read bytecode before definition; " + locate(loader, anchor, domain),
                        e);
                context.diagnostics().recordFailure(className, null, "define", failure, System.nanoTime() - started);
                throw failure;
            }
        }
        try {
            Class<?> defined = java8Runtime()
                    ? defineJava8(type, loader, domain)
                    : defineOnNeighbor(type, anchor, loader, domain);
            remember(loader, className);
            if (defined.getClassLoader() != loader) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.DEFINITION,
                        className,
                        null,
                        "define",
                        "defined class loader does not match the requested loader; "
                                + locate(loader, anchor, domain),
                        null);
            }
            if (defined.getProtectionDomain() != domain) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.DEFINITION,
                        className,
                        null,
                        "define",
                        "defined protection domain does not match the requested domain; "
                                + locate(loader, anchor, domain),
                        null);
            }
            long elapsed = System.nanoTime() - started;
            context.diagnostics().recordDefine(className, loader, resultBytes, elapsed);
            StartupPhaseTrace phases = StartupPhaseTrace.lookup(context);
            if (phases != null) {
                phases.record("define", elapsed);
            }
            return defined;
        } catch (EnhancementFailure failure) {
            context.diagnostics().recordFailure(className, null, "define", failure, System.nanoTime() - started);
            throw failure;
        } catch (Throwable thrown) {
            EnhancementFailure failure = new EnhancementFailure(
                    EnhancementFailure.Category.DEFINITION,
                    className,
                    null,
                    "define",
                    "defineClass failed; " + locate(loader, anchor, domain),
                    thrown instanceof Exception ? (Exception) thrown : new RuntimeException(thrown));
            context.diagnostics().recordFailure(className, null, "define", failure, System.nanoTime() - started);
            throw failure;
        }
    }

    /**
     * Called by {@link EnhancementContext#close()}: rejects further use and drops
     * the anchor and defined-class records so this definer no longer pins them.
     */
    void markClosed() {
        closed = true;
        anchors.clear();
        definedByLoader.clear();
        bootstrapDefined.clear();
    }

    private void ensureThisOpen() {
        if (closed) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.LIFECYCLE,
                    null,
                    null,
                    "define",
                    "class definer is closed",
                    null);
        }
    }

    private Class<?> resolveAnchor(String packageName, ClassLoader loader, ProtectionDomain domain, String className) {
        AnchorKey key = new AnchorKey(packageName, loader);
        Class<?> anchor = anchors.get(key);
        if (anchor == null) {
            String anchorName = anchorClassName(packageName);
            try {
                anchor = Class.forName(anchorName, false, loader);
            } catch (ClassNotFoundException e) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.DEFINITION,
                        className,
                        null,
                        "define",
                        "no anchor registered for package " + packageName
                                + " and " + anchorName + " is not in the target loader; "
                                + locate(loader, null, domain),
                        e);
            }
        }
        rejectNamedModule(anchor, "define");
        if (anchor.getClassLoader() != loader) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.DEFINITION,
                    className,
                    null,
                    "define",
                    "anchor " + anchor.getName() + " is not defined by the target loader; "
                            + locate(loader, anchor, domain),
                    null);
        }
        if (!packageName(anchor).equals(packageName)) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.DEFINITION,
                    className,
                    null,
                    "define",
                    "anchor package " + packageName(anchor) + " does not match " + packageName + "; "
                            + locate(loader, anchor, domain),
                    null);
        }
        ProtectionDomain anchorDomain = anchor.getProtectionDomain();
        if (anchorDomain != domain) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.DEFINITION,
                    className,
                    null,
                    "define",
                    "anchor protection domain does not match the requested domain; "
                            + locate(loader, anchor, domain),
                    null);
        }
        return anchor;
    }

    private void enforceVersion(CtClass type, ClassLoader loader, ProtectionDomain domain) {
        if (type.isFrozen()) {
            type.defrost();
        }
        ClassFile file = type.getClassFile();
        int major = file.getMajorVersion();
        URL resource = null;
        try {
            resource = type.getURL();
        } catch (NotFoundException e) {
            resource = null;
        }
        if (resource != null && major > JAVA8_MAJOR) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.DEFINITION,
                    type.getName(),
                    null,
                    "define",
                    "existing class file major version " + major + " is newer than Java 8 (52); "
                            + "resource=" + resource + "; " + locate(loader, null, domain),
                    null);
        }
        file.setMajorVersion(JAVA8_MAJOR);
        file.setMinorVersion(0);
    }

    /**
     * Locating detail for define-phase failures: loader identity, the anchor's
     * loader and code source, the requested domain's code source and the JDK
     * version. Environment variables and class bytes are never included.
     */
    private static String locate(ClassLoader loader, Class<?> anchor, ProtectionDomain requested) {
        StringBuilder builder = new StringBuilder();
        builder.append("loader=").append(EnhancementDiagnostics.loaderId(loader));
        if (anchor != null) {
            builder.append(" anchor=").append(anchor.getName());
            builder.append(" anchorLoader=").append(EnhancementDiagnostics.loaderId(anchor.getClassLoader()));
            builder.append(" anchorCodeSource=").append(codeSource(anchor.getProtectionDomain()));
        }
        if (requested != null) {
            builder.append(" requestedCodeSource=").append(codeSource(requested));
        }
        builder.append(" jdk=").append(System.getProperty("java.specification.version", "?"));
        return builder.toString();
    }

    private static String codeSource(ProtectionDomain domain) {
        if (domain == null) {
            return "-";
        }
        CodeSource source = domain.getCodeSource();
        URL location = source == null ? null : source.getLocation();
        return location == null ? "-" : String.valueOf(location);
    }

    /**
     * JDK 8 entry. Kept as its own method so the neighbor call is not executed here.
     */
    private Class<?> defineJava8(CtClass type, ClassLoader loader, ProtectionDomain domain) throws CannotCompileException {
        return type.toClass(loader, domain);
    }

    /**
     * Javassist's compiled four-argument neighbor entry. Invoked only when the
     * running runtime is not Java 8.
     */
    private Class<?> defineOnNeighbor(
            CtClass type,
            Class<?> neighbor,
            ClassLoader loader,
            ProtectionDomain domain) throws CannotCompileException {
        return type.getClassPool().toClass(type, neighbor, loader, domain);
    }

    private static boolean java8Runtime() {
        String spec = System.getProperty("java.specification.version", "");
        return spec.startsWith("1.");
    }

    private static void rejectNamedModule(Class<?> anchor, String phase) {
        if (isNamedModule(anchor)) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.UNSUPPORTED,
                    anchor.getName(),
                    null,
                    phase,
                    "named modules are not supported; use the unnamed classpath module",
                    null);
        }
    }

    private static EnhancementFailure moduleFailure(Class<?> type, Exception cause) {
        return new EnhancementFailure(
                EnhancementFailure.Category.UNSUPPORTED,
                type.getName(),
                null,
                "module",
                "cannot inspect module",
                cause);
    }

    static String packageName(Class<?> type) {
        String name = type.getName();
        int dollar = name.indexOf('$');
        if (dollar >= 0) {
            name = name.substring(0, dollar);
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(0, dot);
    }

    static String packageName(CtClass type) {
        String declared = type.getPackageName();
        if (declared != null) {
            return declared;
        }
        String name = type.getName();
        int dollar = name.indexOf('$');
        if (dollar >= 0) {
            name = name.substring(0, dollar);
        }
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(0, dot);
    }

    static String anchorClassName(String packageName) {
        if (packageName == null || packageName.length() == 0) {
            return ANCHOR_SIMPLE_NAME;
        }
        return packageName + "." + ANCHOR_SIMPLE_NAME;
    }

    private boolean alreadyDefined(ClassLoader loader, String className) {
        if (loader == null) {
            return bootstrapDefined.contains(className);
        }
        Set<String> names = definedByLoader.get(loader);
        return names != null && names.contains(className);
    }

    private void remember(ClassLoader loader, String className) {
        if (loader == null) {
            bootstrapDefined.add(className);
            return;
        }
        Set<String> names = definedByLoader.get(loader);
        if (names == null) {
            names = new HashSet<String>();
            definedByLoader.put(loader, names);
        }
        names.add(className);
    }

    /**
     * Package name plus loader identity. IdentityHashMap cannot key on a composite,
     * so the package name is checked in equals and the loader uses identity.
     */
    private static final class AnchorKey {
        private final String packageName;
        private final ClassLoader loader;

        private AnchorKey(String packageName, ClassLoader loader) {
            this.packageName = packageName;
            this.loader = loader;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof AnchorKey)) {
                return false;
            }
            AnchorKey key = (AnchorKey) other;
            return loader == key.loader && packageName.equals(key.packageName);
        }

        @Override
        public int hashCode() {
            return packageName.hashCode() * 31 + System.identityHashCode(loader);
        }
    }

    /**
     * HashMap with identity semantics for the loader stored inside {@link AnchorKey}.
     * AnchorKey already implements identity equality, so a normal HashMap is correct.
     */
    private static final class IdentityAnchorMap extends java.util.HashMap<AnchorKey, Class<?>> {
    }
}
