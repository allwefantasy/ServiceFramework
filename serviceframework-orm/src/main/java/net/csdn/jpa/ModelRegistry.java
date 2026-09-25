package net.csdn.jpa;

import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.jpa.model.Model;

import javax.persistence.Entity;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonical model classes are keyed by binary name. Entity names and simple names
 * are aliases. {@link #values()} never repeats a class that is also stored under an alias.
 * A simple name resolves only when it identifies exactly one class.
 */
public final class ModelRegistry {

    private final Map<String, Class<? extends Model>> byBinaryName = new LinkedHashMap<String, Class<? extends Model>>();
    private final Map<String, List<Class<? extends Model>>> aliases = new LinkedHashMap<String, List<Class<? extends Model>>>();

    public void register(Class<? extends Model> type) {
        register(type, null);
    }

    public void register(Class<? extends Model> type, String alias) {
        if (type == null) {
            throw failure(null, "model class is required");
        }
        String binaryName = type.getName();
        Class<? extends Model> existing = byBinaryName.get(binaryName);
        if (existing != null && existing != type) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFLICT,
                    binaryName,
                    null,
                    "register",
                    "a different class is already registered for " + binaryName,
                    null);
        }
        byBinaryName.put(binaryName, type);
        index(binaryName, type);
        Entity entity = type.getAnnotation(Entity.class);
        if (entity != null && entity.name() != null && entity.name().length() > 0) {
            index(entity.name(), type);
        }
        index(type.getSimpleName(), type);
        if (alias != null && alias.length() > 0) {
            index(alias, type);
        }
    }

    public Class<? extends Model> resolve(String name) {
        List<Class<? extends Model>> found = candidates(name);
        if (found.isEmpty()) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    name,
                    null,
                    "resolve",
                    "model not found",
                    null);
        }
        if (found.size() > 1) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFLICT,
                    name,
                    null,
                    "resolve",
                    "ambiguous model name matches " + describe(found),
                    null);
        }
        return found.get(0);
    }

    public Class<? extends Model> get(String name) {
        List<Class<? extends Model>> found = candidates(name);
        if (found.isEmpty()) {
            return null;
        }
        if (found.size() > 1) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFLICT,
                    name,
                    null,
                    "resolve",
                    "ambiguous model name matches " + describe(found),
                    null);
        }
        return found.get(0);
    }

    public boolean isAmbiguous(String name) {
        return candidates(name).size() > 1;
    }

    public Collection<Class<? extends Model>> values() {
        return Collections.unmodifiableCollection(new ArrayList<Class<? extends Model>>(byBinaryName.values()));
    }

    public int size() {
        return byBinaryName.size();
    }

    public void clear() {
        byBinaryName.clear();
        aliases.clear();
    }

    private List<Class<? extends Model>> candidates(String name) {
        if (name == null) {
            return Collections.emptyList();
        }
        List<Class<? extends Model>> found = aliases.get(name);
        if (found == null || found.isEmpty()) {
            return Collections.emptyList();
        }
        return new ArrayList<Class<? extends Model>>(found);
    }

    private void index(String key, Class<? extends Model> type) {
        List<Class<? extends Model>> found = aliases.get(key);
        if (found == null) {
            found = new ArrayList<Class<? extends Model>>();
            aliases.put(key, found);
        }
        if (!found.contains(type)) {
            found.add(type);
        }
    }

    private static String describe(List<Class<? extends Model>> found) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < found.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(found.get(i).getName());
        }
        return builder.toString();
    }

    private static EnhancementFailure failure(String className, String detail) {
        return new EnhancementFailure(
                EnhancementFailure.Category.CONFIGURATION,
                className,
                null,
                "register",
                detail,
                null);
    }
}
