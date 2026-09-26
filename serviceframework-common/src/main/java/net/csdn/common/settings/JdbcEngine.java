package net.csdn.common.settings;

import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.settings.Settings;

import java.util.Locale;
import java.util.Map;

/**
 * Engine selection shared by metadata, Hibernate properties and pools.
 * The canonical PostgreSQL spelling in configuration is {@code postgres};
 * {@code postgresql} is accepted as an alias only for selectors and engine keys.
 */
public final class JdbcEngine {
    public static final String MYSQL = "mysql";
    public static final String POSTGRES = "postgres";

    private JdbcEngine() {
    }

    public static Selection primary(Settings settings, String mode) {
        if (settings == null || mode == null || mode.length() == 0) {
            throw failure("settings and mode are required", null);
        }
        String selected = trim(settings.get(mode + ".datasources.primary"));
        boolean explicit = selected != null;
        String engine = explicit ? normalize(selected) : MYSQL;
        Settings group = group(settings, mode, engine);
        boolean disabled = group != null && Boolean.TRUE.equals(group.getAsBoolean("disable", false));
        if (group == null && !explicit && Boolean.TRUE.equals(settings.getAsBoolean(
                mode + ".datasources.mysql.disable", false))) {
            return new Selection(MYSQL, null, true, false);
        }
        if (group == null) {
            throw failure("selected datasource " + engine + " is missing; configure "
                    + mode + ".datasources." + groupName(engine), null);
        }
        requireGroupEngine(group, engine, mode + ".datasources." + groupName(engine));
        return new Selection(engine, group, disabled, explicit);
    }

    public static boolean primaryDisabled(Settings settings, String mode) {
        return primary(settings, mode).disabled();
    }

    public static Settings group(Settings settings, String mode, String engine) {
        Settings group = settings.getByPrefix(mode + ".datasources." + groupName(engine) + ".");
        return group.getAsMap().isEmpty() ? null : group;
    }

    public static String normalize(String value) {
        String normalized = trim(value);
        if (normalized == null) {
            throw failure("datasource engine is required", null);
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (MYSQL.equals(normalized)) {
            return MYSQL;
        }
        if (POSTGRES.equals(normalized) || "postgresql".equals(normalized)) {
            return POSTGRES;
        }
        throw failure("unsupported datasource engine " + value + "; use mysql or postgres", null);
    }

    public static String groupName(String engine) {
        return normalize(engine);
    }

    public static String groupEngine(Settings group, String defaultEngine) {
        if (group == null) {
            throw failure("datasource settings are required", null);
        }
        String configured = trim(group.get("engine"));
        if (configured == null) {
            configured = trim(group.get("type"));
        }
        String engine = configured == null ? normalize(defaultEngine) : normalize(configured);
        if (!engine.equals(normalize(defaultEngine))) {
            throw failure("datasource engine " + engine + " does not match expected engine "
                    + normalize(defaultEngine), null);
        }
        return engine;
    }

    public static void requireGroupEngine(Settings group, String engine, String location) {
        try {
            groupEngine(group, engine);
        } catch (EnhancementFailure failure) {
            throw failure("invalid engine for " + location + ": " + failure.getDetail(), failure);
        }
    }

    public static String defaultDriver(String engine) {
        if (POSTGRES.equals(normalize(engine))) {
            return "org.postgresql.Driver";
        }
        return "com.mysql.jdbc.Driver";
    }

    public static String defaultDialect(String engine) {
        if (POSTGRES.equals(normalize(engine))) {
            return "org.hibernate.dialect.PostgreSQL95Dialect";
        }
        return "org.hibernate.dialect.MySQLDialect";
    }

    public static String defaultDbType(String engine) {
        if (POSTGRES.equals(normalize(engine))) {
            return "net.csdn.jpa.type.impl.PostgresType";
        }
        return "net.csdn.jpa.type.impl.MysqlType";
    }

    /**
     * PostgreSQL schema stored by JDBC metadata. A surrounding pair of double
     * quotes is accepted in configuration and removed for metadata lookups.
     */
    public static String schema(Settings group) {
        String configured = trim(group == null ? null : group.get("schema"));
        String schema = configured == null ? "public" : configured;
        if (schema.length() > 1 && schema.charAt(0) == '"' && schema.charAt(schema.length() - 1) == '"') {
            schema = schema.substring(1, schema.length() - 1).replace("\"\"", "\"");
        }
        if (schema.length() == 0 || schema.indexOf(',') >= 0 || schema.indexOf(';') >= 0) {
            throw failure("postgres schema must be one schema name", null);
        }
        return schema;
    }

    /**
     * The configured schema rendered for Hibernate's {@code hibernate.default_schema}
     * and for the JDBC {@code currentSchema} search path. Safe lowercase identifiers
     * stay bare; any other name is wrapped in double quotes with inner quotes
     * doubled, the same spelling PostgreSQL accepts and Hibernate treats as a
     * quoted identifier.
     */
    public static String quotedSchema(Settings group) {
        return searchPathValue(schema(group));
    }

    public static String jdbcUrl(Settings group, String engineName) {
        String engine = normalize(engineName);
        requireGroupEngine(group, engine, "datasource");
        String host = required(group, "host");
        String port = required(group, "port");
        String database = required(group, "database");
        Map<String, String> jdbcOpts = group.getByPrefix("jdbc.").getAsMap();
        StringBuilder query = new StringBuilder();
        if (MYSQL.equals(engine)) {
            query.append("?useUnicode=true&characterEncoding=utf8");
        } else {
            String schema = schema(group);
            String currentSchema = trim(jdbcOpts.get("currentSchema"));
            if (currentSchema != null) {
                String normalized = unquote(currentSchema);
                if (!schema.equals(normalized)) {
                    throw failure("postgres schema and jdbc.currentSchema disagree", null);
                }
            }
            appendOption(query, "currentSchema", searchPathValue(schema));
        }
        for (Map.Entry<String, String> entry : jdbcOpts.entrySet()) {
            if (POSTGRES.equals(engine) && "currentSchema".equals(entry.getKey())) {
                continue;
            }
            appendOption(query, entry.getKey(), entry.getValue());
        }
        String prefix = POSTGRES.equals(engine) ? "jdbc:postgresql://" : "jdbc:mysql://";
        return prefix + host + ":" + port + "/" + database + query;
    }

    private static String searchPathValue(String schema) {
        if (schema.matches("[a-z_][a-z0-9_]*")) {
            return schema;
        }
        return '"' + schema.replace("\"", "\"\"") + '"';
    }

    private static String unquote(String value) {
        String text = trim(value);
        if (text != null && text.length() > 1 && text.charAt(0) == '"' && text.charAt(text.length() - 1) == '"') {
            return text.substring(1, text.length() - 1).replace("\"\"", "\"");
        }
        return text;
    }

    private static void appendOption(StringBuilder query, String key, String value) {
        try {
            query.append(query.length() == 0 ? '?' : '&');
            query.append(key).append('=');
            query.append(java.net.URLEncoder.encode(value == null ? "" : value, "UTF-8"));
        } catch (java.io.UnsupportedEncodingException e) {
            throw failure("jdbc option was not encoded", e);
        }
    }

    private static String required(Settings group, String key) {
        String value = trim(group.get(key));
        if (value == null) {
            throw failure("datasource " + key + " is required", null);
        }
        return value;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    private static EnhancementFailure failure(String detail, Throwable cause) {
        return new EnhancementFailure(
                EnhancementFailure.Category.CONFIGURATION,
                null,
                null,
                "datasource",
                detail,
                cause);
    }

    public static final class Selection {
        private final String engine;
        private final Settings group;
        private final boolean disabled;
        private final boolean explicit;

        private Selection(String engine, Settings group, boolean disabled, boolean explicit) {
            this.engine = engine;
            this.group = group;
            this.disabled = disabled;
            this.explicit = explicit;
        }

        public String engine() {
            return engine;
        }

        public String groupName() {
            return JdbcEngine.groupName(engine);
        }

        public Settings group() {
            return group;
        }

        public boolean disabled() {
            return disabled;
        }

        public boolean explicit() {
            return explicit;
        }
    }
}
