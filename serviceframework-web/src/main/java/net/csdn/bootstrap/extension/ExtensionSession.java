package net.csdn.bootstrap.extension;

import net.csdn.bootstrap.ApplicationContext;
import net.csdn.bootstrap.FrameworkExtension;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.settings.Settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Loads extension descriptors, orders the enabled ones, and closes them in
 * reverse start order. A config-disabled name is stored and never passed to
 * {@link Class#forName}.
 */
public final class ExtensionSession {

    private final List<String> disabledNames = new ArrayList<String>();
    private final List<FrameworkExtension> ordered = new ArrayList<FrameworkExtension>();
    private final List<FrameworkExtension> registered = new ArrayList<FrameworkExtension>();
    private final List<FrameworkExtension> started = new ArrayList<FrameworkExtension>();
    private boolean closed;

    public List<String> disabledNames() {
        return Collections.unmodifiableList(new ArrayList<String>(disabledNames));
    }

    public void prepare(ApplicationContext context, Settings settings, List<FrameworkExtension> programmatic) {
        List<Descriptor> descriptors = new ArrayList<Descriptor>();
        boolean mysql = !Boolean.TRUE.equals(settings.getAsBoolean(
                context.mode().name() + ".datasources.mysql.disable", Boolean.FALSE));
        boolean mongo = !Boolean.TRUE.equals(settings.getAsBoolean(
                context.mode().name() + ".datasources.mongodb.disable", Boolean.TRUE));
        descriptors.add(new Descriptor(BuiltinExtensions.ORM, mysql));
        descriptors.add(new Descriptor(BuiltinExtensions.MONGO, mongo));
        for (String name : WowCollections.split2(settings.get("application.extensions"), ",")) {
            if (name.trim().length() > 0) {
                descriptors.add(new Descriptor(name.trim(), true));
            }
        }
        for (String name : WowCollections.split2(settings.get("application.extensions.disabled"), ",")) {
            if (name.trim().length() > 0) {
                descriptors.add(new Descriptor(name.trim(), false));
            }
        }
        List<FrameworkExtension> enabled = new ArrayList<FrameworkExtension>();
        if (programmatic != null) {
            enabled.addAll(programmatic);
        }
        ClassLoader loader = context.targetLoader();
        for (int i = 0; i < descriptors.size(); i++) {
            Descriptor descriptor = descriptors.get(i);
            if (!descriptor.enabled) {
                disabledNames.add(descriptor.className);
                continue;
            }
            enabled.add(instantiate(descriptor.className, loader));
        }
        List<FrameworkExtension> active = new ArrayList<FrameworkExtension>();
        for (int i = 0; i < enabled.size(); i++) {
            FrameworkExtension extension = enabled.get(i);
            if (!extension.enabled(settings, context)) {
                disabledNames.add(extension.getClass().getName());
                continue;
            }
            active.add(extension);
        }
        ordered.addAll(order(active));
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).validate(settings, context);
        }
    }

    public void registerAll(ApplicationContext context, Settings settings) {
        for (int i = 0; i < ordered.size(); i++) {
            FrameworkExtension extension = ordered.get(i);
            try {
                extension.register(settings, context);
                registered.add(extension);
            } catch (Throwable thrown) {
                try {
                    extension.close();
                } catch (Throwable suppressed) {
                    thrown.addSuppressed(suppressed);
                }
                throw thrown;
            }
        }
    }

    public void startAll(ApplicationContext context, Settings settings) {
        for (int i = 0; i < ordered.size(); i++) {
            FrameworkExtension extension = ordered.get(i);
            try {
                extension.start(settings, context);
                started.add(extension);
            } catch (Throwable thrown) {
                try {
                    extension.close();
                } catch (Throwable suppressed) {
                    thrown.addSuppressed(suppressed);
                }
                throw thrown;
            }
        }
    }

    public void closeAll() {
        if (closed) {
            return;
        }
        closed = true;
        List<Throwable> failures = new ArrayList<Throwable>();
        Set<FrameworkExtension> seen = Collections.newSetFromMap(new java.util.IdentityHashMap<FrameworkExtension, Boolean>());
        for (int i = started.size() - 1; i >= 0; i--) {
            closeOne(started.get(i), seen, failures);
        }
        for (int i = registered.size() - 1; i >= 0; i--) {
            closeOne(registered.get(i), seen, failures);
        }
        if (failures.isEmpty()) {
            return;
        }
        Throwable primary = failures.get(0);
        for (int i = 1; i < failures.size(); i++) {
            primary.addSuppressed(failures.get(i));
        }
        if (primary instanceof Error) {
            throw (Error) primary;
        }
        if (primary instanceof RuntimeException) {
            throw (RuntimeException) primary;
        }
        throw new EnhancementFailure(
                EnhancementFailure.Category.LIFECYCLE,
                null,
                null,
                "close",
                "extension close failed",
                primary instanceof Exception ? (Exception) primary : new RuntimeException(primary));
    }

    private static void closeOne(FrameworkExtension extension, Set<FrameworkExtension> seen, List<Throwable> failures) {
        if (!seen.add(extension)) {
            return;
        }
        try {
            extension.close();
        } catch (Throwable thrown) {
            failures.add(thrown);
        }
    }

    private static FrameworkExtension instantiate(String className, ClassLoader loader) {
        Class<?> type;
        try {
            type = Class.forName(className, false, loader);
        } catch (ClassNotFoundException e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    className,
                    null,
                    "extension",
                    "enabled extension class was not found",
                    e);
        }
        if (!FrameworkExtension.class.isAssignableFrom(type)) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    className,
                    null,
                    "extension",
                    "extension class does not implement FrameworkExtension",
                    null);
        }
        try {
            java.lang.reflect.Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            return (FrameworkExtension) constructor.newInstance();
        } catch (Exception e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    className,
                    null,
                    "extension",
                    "extension class needs an accessible no-arg constructor",
                    e);
        }
    }

    static List<FrameworkExtension> order(List<FrameworkExtension> extensions) {
        Map<String, FrameworkExtension> byId = new LinkedHashMap<String, FrameworkExtension>();
        Map<String, Integer> index = new HashMap<String, Integer>();
        for (int i = 0; i < extensions.size(); i++) {
            FrameworkExtension extension = extensions.get(i);
            if (extension == null || extension.id() == null || extension.id().trim().length() == 0) {
                throw graph(extension == null ? null : extension.getClass().getName(), "extension id is required");
            }
            if (byId.containsKey(extension.id())) {
                throw graph(extension.getClass().getName(), "duplicate extension id " + extension.id());
            }
            byId.put(extension.id(), extension);
            index.put(extension.id(), Integer.valueOf(i));
        }
        Map<String, FrameworkExtension> providers = new LinkedHashMap<String, FrameworkExtension>();
        for (FrameworkExtension extension : byId.values()) {
            List<String> provides = extension.provides();
            if (provides == null || provides.isEmpty()) {
                throw graph(extension.getClass().getName(), "extension " + extension.id() + " provides nothing");
            }
            for (int i = 0; i < provides.size(); i++) {
                String capability = provides.get(i);
                if (capability == null || capability.trim().length() == 0) {
                    throw graph(extension.getClass().getName(), "blank capability from " + extension.id());
                }
                FrameworkExtension previous = providers.get(capability);
                if (previous != null) {
                    throw graph(extension.getClass().getName(),
                            "capability " + capability + " is provided by both " + previous.id() + " and " + extension.id());
                }
                providers.put(capability, extension);
            }
        }
        Map<String, List<String>> outgoing = new LinkedHashMap<String, List<String>>();
        Map<String, Integer> indegree = new HashMap<String, Integer>();
        for (String id : byId.keySet()) {
            outgoing.put(id, new ArrayList<String>());
            indegree.put(id, Integer.valueOf(0));
        }
        for (FrameworkExtension extension : byId.values()) {
            List<String> requires = extension.requires();
            if (requires == null) {
                continue;
            }
            Set<String> seen = new HashSet<String>();
            for (int i = 0; i < requires.size(); i++) {
                String capability = requires.get(i);
                if (capability == null || capability.trim().length() == 0) {
                    throw graph(extension.getClass().getName(), "blank requirement from " + extension.id());
                }
                if (!seen.add(capability)) {
                    throw graph(extension.getClass().getName(), "duplicate requirement " + capability + " on " + extension.id());
                }
                FrameworkExtension provider = providers.get(capability);
                if (provider == null) {
                    throw graph(extension.getClass().getName(),
                            "extension " + extension.id() + " requires unknown capability " + capability);
                }
                if (provider.id().equals(extension.id())) {
                    throw graph(extension.getClass().getName(), "extension " + extension.id() + " requires itself");
                }
                List<String> next = outgoing.get(provider.id());
                if (!next.contains(extension.id())) {
                    next.add(extension.id());
                    indegree.put(extension.id(), Integer.valueOf(indegree.get(extension.id()).intValue() + 1));
                }
            }
        }
        PriorityQueue<String> ready = new PriorityQueue<String>(Math.max(1, byId.size()), new Comparator<String>() {
            @Override
            public int compare(String left, String right) {
                return index.get(left).intValue() - index.get(right).intValue();
            }
        });
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue().intValue() == 0) {
                ready.add(entry.getKey());
            }
        }
        List<FrameworkExtension> result = new ArrayList<FrameworkExtension>();
        while (!ready.isEmpty()) {
            String id = ready.poll();
            result.add(byId.get(id));
            List<String> next = outgoing.get(id);
            for (int i = 0; i < next.size(); i++) {
                String target = next.get(i);
                int degree = indegree.get(target).intValue() - 1;
                indegree.put(target, Integer.valueOf(degree));
                if (degree == 0) {
                    ready.add(target);
                }
            }
        }
        if (result.size() != byId.size()) {
            throw graph(null, "extension requirements contain a cycle");
        }
        return result;
    }

    private static EnhancementFailure graph(String className, String detail) {
        return new EnhancementFailure(
                EnhancementFailure.Category.DEPENDENCY,
                className,
                null,
                "extension",
                detail,
                null);
    }

    private static final class Descriptor {
        private final String className;
        private final boolean enabled;

        private Descriptor(String className, boolean enabled) {
            this.className = className;
            this.enabled = enabled;
        }
    }
}
