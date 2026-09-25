package net.csdn.jpa.type;

import net.csdn.common.Strings;
import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Schema snapshot for one configured database. Column types come from
 * {@link DatabaseMetaData#getColumns} and are normalized to the {@code TYPE_NAME}
 * values {@link net.csdn.jpa.type.impl.MysqlType} understands. Refresh replaces
 * the snapshot; it does not append. The snapshot identity includes host, port,
 * configured database and the connected catalog, so a result without that identity
 * is never reused.
 * <p>
 * {@link #schemaSnapshot()} is the sorted table, column and type text of that
 * snapshot. {@link #schemaDigest()} is its SHA-256. Neither includes the
 * connection identity, JDBC URL, user or password. The digest is not cached:
 * each call hashes the snapshot held at that moment. Refresh replaces the
 * snapshot, so the next digest changes with it. Configuring ORM does not call
 * {@link #schemaDigest()} unless diagnostics are enabled.
 */
public class DBInfo {

    /**
     * Diagnostics version token recorded on ORM enhancement events.
     * This is not a settings dump and it does not name a server or user.
     */
    public static final String DIAGNOSTICS_VERSION = "orm-1";

    private static final String AMBIGUOUS = "\u0000ambiguous";
    private static final String SCHEMA_HEADER = "db-schema v1";

    private final Settings settings;
    private final boolean disabled;
    private final String host;
    private final String port;
    private final String database;
    private final String username;
    private final String password;
    private final String url;
    private final Map<String, String> aliasToTable = new LinkedHashMap<String, String>();
    private volatile Snapshot snapshot = Snapshot.empty();
    private volatile RefreshStats lastRefreshStats = RefreshStats.empty();
    private int schemaDigestComputations;

    public DBInfo(Settings settings) {
        this.settings = settings;
        Settings mysql = mysqlSettings(settings);
        this.disabled = mysql == null || settings.getAsBoolean(JPA.mode() + ".datasources.mysql.disable", false);
        this.host = mysql == null ? "" : value(mysql.get("host"));
        this.port = mysql == null ? "" : value(mysql.get("port"));
        this.database = mysql == null ? "" : value(mysql.get("database"));
        this.username = mysql == null ? "" : value(mysql.get("username"));
        this.password = mysql == null ? "" : value(mysql.get("password"));
        this.url = mysql == null ? "" : JPA.properties(mysql).get("url");
        if (!disabled) {
            try {
                refresh();
            } catch (EnhancementFailure failure) {
                throw failure;
            } catch (Exception e) {
                throw metadataFailure("schema metadata was not read", e);
            }
        }
    }

    public void info() {
        refresh();
    }

    public void refresh() {
        if (disabled) {
            throw metadataFailure("mysql is disabled", null);
        }
        long started = System.nanoTime();
        int tableCalls = 0;
        int columnCalls = 0;
        loadDriver();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            String catalog = connection.getCatalog();
            if (catalog == null || catalog.length() == 0 || !catalog.equalsIgnoreCase(database)) {
                throw metadataFailure("connected catalog does not match configured database", null);
            }
            DatabaseMetaData metaData = connection.getMetaData();
            Map<String, Map<String, String>> columnsByTable = new LinkedHashMap<String, Map<String, String>>();
            List<String> names = new ArrayList<String>();
            tableCalls++;
            try (ResultSet tables = metaData.getTables(catalog, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableCatalog = tables.getString("TABLE_CAT");
                    if (tableCatalog != null && !tableCatalog.equalsIgnoreCase(catalog)) {
                        continue;
                    }
                    String tableName = tables.getString("TABLE_NAME");
                    if (tableName == null || columnsByTable.containsKey(tableName)) {
                        continue;
                    }
                    names.add(tableName);
                    columnsByTable.put(tableName, new LinkedHashMap<String, String>());
                }
            }
            columnCalls++;
            try (ResultSet columns = metaData.getColumns(catalog, null, "%", "%")) {
                while (columns.next()) {
                    String tableCatalog = columns.getString("TABLE_CAT");
                    if (tableCatalog != null && !tableCatalog.equalsIgnoreCase(catalog)) {
                        continue;
                    }
                    String tableName = columns.getString("TABLE_NAME");
                    String columnName = columns.getString("COLUMN_NAME");
                    String typeName = columns.getString("TYPE_NAME");
                    Map<String, String> table = columnsByTable.get(tableName);
                    if (table == null || columnName == null) {
                        continue;
                    }
                    table.put(columnName, normalizeTypeName(typeName));
                }
            }
            String identity = identity(host, port, database, catalog);
            Snapshot next = new Snapshot(identity, names, columnsByTable);
            this.snapshot = next;
            rememberTableAliases(names);
            this.lastRefreshStats = new RefreshStats(identity, names.size(), tableCalls, columnCalls, System.nanoTime() - started);
        } catch (EnhancementFailure failure) {
            throw failure;
        } catch (SQLException e) {
            throw metadataFailure("schema metadata was not read", e);
        }
    }

    public List<String> tableNames() {
        return Collections.unmodifiableList(new ArrayList<String>(snapshot.tableNames));
    }

    public boolean hasTable(String name) {
        if (name == null) {
            return false;
        }
        Snapshot current = snapshot;
        if (current.columnsByTable.containsKey(name)) {
            return true;
        }
        for (String table : current.columnsByTable.keySet()) {
            if (table.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, String> columns(String key) {
        if (snapshot.identity == null || snapshot.identity.length() == 0) {
            return null;
        }
        if (database.length() > 0 && snapshot.identity.indexOf("/" + database + "#") < 0) {
            throw metadataFailure("schema snapshot does not include the configured database", null);
        }
        if (key == null) {
            return null;
        }
        Snapshot current = snapshot;
        Map<String, String> direct = current.columnsByTable.get(key);
        if (direct != null) {
            return Collections.unmodifiableMap(direct);
        }
        for (Map.Entry<String, Map<String, String>> entry : current.columnsByTable.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return Collections.unmodifiableMap(entry.getValue());
            }
        }
        String table = aliasToTable.get(key);
        if (table == null || AMBIGUOUS.equals(table)) {
            return null;
        }
        Map<String, String> aliased = current.columnsByTable.get(table);
        if (aliased == null) {
            return null;
        }
        return Collections.unmodifiableMap(aliased);
    }

    public void bind(String key, String table) {
        if (key == null || key.length() == 0 || table == null || table.length() == 0) {
            return;
        }
        String physical = resolveTableName(table);
        if (physical == null) {
            aliasToTable.put(key, table);
            return;
        }
        String existing = aliasToTable.get(key);
        if (existing == null || existing.equals(physical)) {
            aliasToTable.put(key, physical);
            return;
        }
        if (!AMBIGUOUS.equals(existing)) {
            aliasToTable.put(key, AMBIGUOUS);
        }
    }

    public String identity() {
        return snapshot.identity;
    }

    public RefreshStats lastRefreshStats() {
        return lastRefreshStats;
    }

    /**
     * Canonical schema text for the current snapshot. Tables and columns are
     * sorted by name. The connection identity is not included. This does not
     * open a connection and does not cache the text.
     */
    public String schemaSnapshot() {
        Snapshot current = snapshot;
        List<String> tables = new ArrayList<String>(current.columnsByTable.keySet());
        Collections.sort(tables);
        StringBuilder builder = new StringBuilder();
        builder.append(SCHEMA_HEADER);
        for (int i = 0; i < tables.size(); i++) {
            String table = tables.get(i);
            builder.append('\n');
            builder.append("table ").append(table);
            Map<String, String> columns = current.columnsByTable.get(table);
            List<String> names = new ArrayList<String>(columns.keySet());
            Collections.sort(names);
            for (int j = 0; j < names.size(); j++) {
                String column = names.get(j);
                String typeName = columns.get(column);
                builder.append('\n');
                builder.append("column ").append(column).append(' ');
                builder.append(typeName == null ? "" : typeName);
            }
        }
        return builder.toString();
    }

    /**
     * SHA-256 hex of {@link #schemaSnapshot()}. Counted on every call, including
     * a repeat against an unchanged snapshot, so a caller can see that the
     * result is not cached.
     */
    public String schemaDigest() {
        schemaDigestComputations++;
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(schemaSnapshot().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (int i = 0; i < bytes.length; i++) {
                int value = bytes[i] & 0xff;
                if (value < 16) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(value));
            }
            return hex.toString();
        } catch (Exception e) {
            throw metadataFailure("schema digest was not computed", e);
        }
    }

    /**
     * How many times this instance hashed the schema snapshot.
     * Stays zero until {@link #schemaDigest()} is called.
     */
    public int schemaDigestComputations() {
        return schemaDigestComputations;
    }

    public static String quoteIdentifier(String identifier) {
        if (identifier == null) {
            throw metadataFailure("identifier is required", null);
        }
        return "`" + identifier.replace("`", "``") + "`";
    }

    /**
     * Maps driver {@code TYPE_NAME} values onto the names {@code MysqlType} looks up.
     * Unknown names are returned unchanged, uppercased, so a new type is not silently remapped.
     */
    public static String normalizeTypeName(String typeName) {
        if (typeName == null) {
            return "";
        }
        String upper = typeName.trim().toUpperCase(Locale.ROOT);
        if (upper.startsWith("BIGINT")) {
            return "BIGINT";
        }
        if (upper.startsWith("SMALLINT")) {
            return "SMALLINT";
        }
        if (upper.startsWith("TINYINT") || upper.equals("BOOL") || upper.equals("BOOLEAN")) {
            if (upper.equals("BOOL") || upper.equals("BOOLEAN")) {
                return "BOOLEAN";
            }
            return "TINYINT";
        }
        if (upper.startsWith("MEDIUMINT") || upper.startsWith("INTEGER") || upper.startsWith("INT")) {
            return "INT";
        }
        if (upper.startsWith("VARCHAR")) {
            return "VARCHAR";
        }
        if (upper.startsWith("CHAR")) {
            return "CHAR";
        }
        if (upper.contains("MEDIUMTEXT")) {
            return "MEDIUMTEXT";
        }
        if (upper.contains("LONGTEXT")) {
            return "LONGTEXT";
        }
        if (upper.equals("TEXT") || upper.endsWith(" TEXT")) {
            return "TEXT";
        }
        if (upper.startsWith("DOUBLE")) {
            return "DOUBLE";
        }
        if (upper.startsWith("FLOAT")) {
            return "FLOAT";
        }
        if (upper.startsWith("DATETIME")) {
            return "DATETIME";
        }
        if (upper.startsWith("TIMESTAMP")) {
            return "TIMESTAMP";
        }
        if (upper.startsWith("DATE")) {
            return "DATE";
        }
        if (upper.startsWith("BIT")) {
            return "BIT";
        }
        return upper;
    }

    private void rememberTableAliases(List<String> names) {
        for (int i = 0; i < names.size(); i++) {
            String table = names.get(i);
            bind(table, table);
            bind(Strings.toCamelCase(table, true), table);
        }
    }

    private String resolveTableName(String table) {
        Snapshot current = snapshot;
        if (current.columnsByTable.containsKey(table)) {
            return table;
        }
        for (String name : current.columnsByTable.keySet()) {
            if (name.equalsIgnoreCase(table)) {
                return name;
            }
        }
        return null;
    }

    private static void loadDriver() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw metadataFailure("mysql driver com.mysql.jdbc.Driver is not on the classpath", e);
        }
    }

    private static Settings mysqlSettings(Settings settings) {
        if (settings == null) {
            throw metadataFailure("settings are required", null);
        }
        Map<String, Settings> groups = settings.getGroups(JPA.mode() + ".datasources");
        if (groups == null) {
            return null;
        }
        return groups.get("mysql");
    }

    private static String identity(String host, String port, String database, String catalog) {
        return "mysql://" + host + ":" + port + "/" + database + "#catalog=" + catalog;
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    private static EnhancementFailure metadataFailure(String detail, Exception cause) {
        return new EnhancementFailure(
                EnhancementFailure.Category.CONFIGURATION,
                null,
                null,
                "schema",
                detail,
                cause);
    }

    public static final class RefreshStats {
        public final String identity;
        public final int tables;
        public final int getTablesCalls;
        public final int getColumnsCalls;
        public final long elapsedNanos;

        public RefreshStats(String identity, int tables, int getTablesCalls, int getColumnsCalls, long elapsedNanos) {
            this.identity = identity;
            this.tables = tables;
            this.getTablesCalls = getTablesCalls;
            this.getColumnsCalls = getColumnsCalls;
            this.elapsedNanos = elapsedNanos;
        }

        static RefreshStats empty() {
            return new RefreshStats("", 0, 0, 0, 0L);
        }
    }

    private static final class Snapshot {
        private final String identity;
        private final List<String> tableNames;
        private final Map<String, Map<String, String>> columnsByTable;

        private Snapshot(String identity, List<String> tableNames, Map<String, Map<String, String>> columnsByTable) {
            this.identity = identity;
            this.tableNames = tableNames;
            this.columnsByTable = columnsByTable;
        }

        private static Snapshot empty() {
            return new Snapshot("", new ArrayList<String>(), new LinkedHashMap<String, Map<String, String>>());
        }
    }
}
