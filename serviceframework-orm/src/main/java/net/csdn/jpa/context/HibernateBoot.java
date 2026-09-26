package net.csdn.jpa.context;

import net.csdn.common.enhancer.EnhancementFailure;
import org.hibernate.jpa.HibernatePersistenceProvider;

import javax.persistence.EntityManagerFactory;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Builds an EntityManagerFactory from classes already defined in the application loader.
 */
public final class HibernateBoot {

    private HibernateBoot() {
    }

    public static EntityManagerFactory create(String unitName, Map<String, String> legacyProperties, List<Class<?>> managed, ClassLoader loader) {
        if (managed == null || managed.isEmpty()) {
            throw failure("no managed classes are registered");
        }
        if (loader == null) {
            throw failure("application loader is required");
        }
        Properties properties = translate(legacyProperties);
        List<String> names = new ArrayList<String>();
        URL root = null;
        for (int i = 0; i < managed.size(); i++) {
            Class<?> type = managed.get(i);
            names.add(type.getName());
            if (root == null && type.getProtectionDomain() != null && type.getProtectionDomain().getCodeSource() != null) {
                root = type.getProtectionDomain().getCodeSource().getLocation();
            }
        }
        if (root == null) {
            root = loader.getResource("");
        }
        ManagedPersistenceUnit unit = new ManagedPersistenceUnit(unitName, names, properties, loader, root);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(loader);
        try {
            return new HibernatePersistenceProvider().createContainerEntityManagerFactory(unit, properties);
        } catch (RuntimeException e) {
            throw new EnhancementFailure(
                    EnhancementFailure.Category.CONFIGURATION,
                    null,
                    null,
                    "bootstrap",
                    "EntityManagerFactory was not created",
                    e);
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    static Properties translate(Map<String, String> legacy) {
        Properties properties = new Properties();
        if (legacy == null) {
            return properties;
        }
        String driver = first(legacy, "driver_class", "javax.persistence.jdbc.driver", "hibernate.connection.driver_class");
        String url = first(legacy, "url", "javax.persistence.jdbc.url", "hibernate.connection.url");
        String user = first(legacy, "username", "javax.persistence.jdbc.user", "hibernate.connection.username");
        String password = first(legacy, "password", "javax.persistence.jdbc.password", "hibernate.connection.password");
        String dialect = first(legacy, "dialect", "hibernate.dialect");
        put(properties, "javax.persistence.jdbc.driver", driver);
        put(properties, "javax.persistence.jdbc.url", url);
        put(properties, "javax.persistence.jdbc.user", user);
        put(properties, "javax.persistence.jdbc.password", password);
        put(properties, "hibernate.connection.driver_class", driver);
        put(properties, "hibernate.connection.url", url);
        put(properties, "hibernate.connection.username", user);
        put(properties, "hibernate.connection.password", password);
        put(properties, "hibernate.dialect", dialect == null ? "org.hibernate.dialect.MySQL5Dialect" : dialect);
        put(properties, "hibernate.show_sql", first(legacy, "show_sql", "hibernate.show_sql"));
        put(properties, "hibernate.format_sql", first(legacy, "format_sql", "hibernate.format_sql"));
        put(properties, "hibernate.hbm2ddl.auto", "none");
        put(properties, "hibernate.temp.use_jdbc_metadata_defaults", "false");
        put(properties, "hibernate.archive.autodetection", "none");
        copyIfPresent(properties, legacy, "hibernate.default_schema");
        copyIfPresent(properties, legacy, "hibernate.default_catalog");
        String provider = legacy.get("hibernate.connection.provider_class");
        if (provider == null) {
            provider = legacy.get("provider_class");
        }
        if (provider != null && providerLoads(provider)) {
            properties.setProperty("hibernate.connection.provider_class", provider);
            put(properties, "url", url);
            put(properties, "username", user);
            put(properties, "password", password);
            put(properties, "driverClassName", driver);
            copyIfPresent(properties, legacy, "maxActive");
            copyIfPresent(properties, legacy, "minIdle");
            copyIfPresent(properties, legacy, "initialSize");
            copyIfPresent(properties, legacy, "maxWait");
            copyIfPresent(properties, legacy, "validationQuery");
        }
        return properties;
    }

    private static boolean providerLoads(String className) {
        if ("net.csdn.hibernate.support.DruidConnectionProvider".equals(className)) {
            try {
                Class.forName(className);
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        }
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void copyIfPresent(Properties properties, Map<String, String> legacy, String key) {
        String value = legacy.get(key);
        if (value != null) {
            properties.setProperty(key, value);
        }
    }

    private static void put(Properties properties, String key, String value) {
        if (value != null) {
            properties.setProperty(key, value);
        }
    }

    private static String first(Map<String, String> legacy, String... keys) {
        for (int i = 0; i < keys.length; i++) {
            String value = legacy.get(keys[i]);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return null;
    }

    private static EnhancementFailure failure(String detail) {
        return new EnhancementFailure(
                EnhancementFailure.Category.CONFIGURATION,
                null,
                null,
                "bootstrap",
                detail,
                null);
    }
}
