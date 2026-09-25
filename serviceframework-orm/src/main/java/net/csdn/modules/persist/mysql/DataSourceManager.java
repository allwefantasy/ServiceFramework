package net.csdn.modules.persist.mysql;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.google.inject.Inject;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;
import net.csdn.jpa.JdbcEndpoints;

import javax.sql.DataSource;
import java.io.Closeable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-6-1
 * Time: 下午9:11
 */
public class DataSourceManager implements Closeable {

    private final Map<String, DataSource> dataSourceMap = new LinkedHashMap<String, DataSource>();
    private final List<DataSource> creationOrder = new ArrayList<DataSource>();
    private final Settings settings;
    private final CSLogger logger = Loggers.getLogger(DataSourceManager.class);
    private boolean closed;

    @Inject
    public DataSourceManager(Settings settings) {
        this.settings = settings;
        if (!JPA.isConfigured()) {
            return;
        }
        buildAll(JPA.mode());
    }

    /**
     * Builds pools from {@code settings} for {@code mode} without reading another application context.
     */
    public static DataSourceManager forMode(Settings settings, String mode) {
        if (settings == null || mode == null || mode.length() == 0) {
            throw new IllegalArgumentException("settings and mode are required");
        }
        DataSourceManager manager = new DataSourceManager(settings, false);
        manager.buildAll(mode);
        return manager;
    }

    /**
     * Does not read the current application. The caller closes this manager.
     */
    public static DataSourceManager standalone(Settings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings are required");
        }
        return new DataSourceManager(settings, false);
    }

    private DataSourceManager(Settings settings, boolean ignored) {
        this.settings = settings;
    }

    public DataSource datasource(String name) {
        return dataSourceMap.get(name);
    }

    public Map<String, DataSource> dataSourceMap() {
        return dataSourceMap;
    }

    private void buildAll(String mode) {
        try {
            if (settings.getAsBoolean(mode + ".datasources.mysql.disable", false)) {
                return;
            }
            Map<String, Settings> groups = settings.getGroups(mode + ".datasources");
            Settings mysql = groups.get("mysql");
            if (mysql != null) {
                dataSourceMap.put("mysql", buildPool(mysql));
            }
            if (groups.get("multi-mysql") != null) {
                Map<String, Settings> mysqlGroups = settings.getGroups(mode + ".datasources.multi-mysql");
                for (Map.Entry<String, Settings> entry : mysqlGroups.entrySet()) {
                    dataSourceMap.put(entry.getKey(), buildPool(entry.getValue()));
                }
            }
        } catch (RuntimeException e) {
            try {
                close();
            } catch (RuntimeException closeFailure) {
                e.addSuppressed(closeFailure);
            }
            throw e;
        }
    }

    public synchronized DataSource buildPool(Settings mysqlSetting) {
        if (closed) {
            throw new IllegalStateException("datasource manager is closed");
        }
        Map<String, String> properties = new HashMap<String, String>(JPA.properties(mysqlSetting));
        String url = properties.get("url");
        // Hold the instance ourselves. The factory's init=true path throws away
        // the pool when the first connection fails, and its create thread keeps running.
        properties.put("init", "false");
        DruidDataSource dataSource;
        try {
            dataSource = (DruidDataSource) DruidDataSourceFactory.createDataSource(properties);
        } catch (Exception e) {
            throw datasourceFailure(url, e);
        }
        try {
            dataSource.init();
        } catch (SQLException e) {
            closeFailed(dataSource);
            throw datasourceFailure(url, e);
        } catch (RuntimeException e) {
            closeFailed(dataSource);
            throw datasourceFailure(url, e);
        }
        creationOrder.add(dataSource);
        return dataSource;
    }

    private static RuntimeException datasourceFailure(String url, Exception cause) {
        String endpoint = JdbcEndpoints.endpoint(url);
        String target = endpoint.length() == 0 ? "" : " " + endpoint;
        return new RuntimeException("can not create datasource" + target, cause);
    }

    private static void closeFailed(DruidDataSource dataSource) {
        if (dataSource == null) {
            return;
        }
        try {
            // A failed init may have started the create thread before setting inited.
            // close() ignores that pool unless the flag is set, so the thread keeps dialing.
            java.lang.reflect.Field inited = com.alibaba.druid.pool.DruidAbstractDataSource.class.getDeclaredField("inited");
            inited.setAccessible(true);
            if (!inited.getBoolean(dataSource)) {
                inited.setBoolean(dataSource, true);
            }
        } catch (ReflectiveOperationException ignored) {
            // close() still runs for a pool that did finish init.
        }
        try {
            dataSource.close();
        } catch (RuntimeException ignored) {
            // The caller reports the original connection failure.
        }
    }

    void trackForClose(DataSource dataSource) {
        creationOrder.add(dataSource);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        List<Throwable> failures = new ArrayList<Throwable>();
        Map<DataSource, Boolean> seen = new IdentityHashMap<DataSource, Boolean>();
        for (int i = creationOrder.size() - 1; i >= 0; i--) {
            DataSource dataSource = creationOrder.get(i);
            if (dataSource == null || seen.put(dataSource, Boolean.TRUE) != null) {
                continue;
            }
            try {
                closeOne(dataSource);
            } catch (Throwable thrown) {
                failures.add(thrown);
            }
        }
        creationOrder.clear();
        dataSourceMap.clear();
        if (!failures.isEmpty()) {
            throw chain(failures);
        }
    }

    private static void closeOne(DataSource dataSource) throws Exception {
        if (dataSource instanceof DruidDataSource) {
            ((DruidDataSource) dataSource).close();
            return;
        }
        if (dataSource instanceof Closeable) {
            ((Closeable) dataSource).close();
        }
    }

    private static RuntimeException chain(List<Throwable> failures) {
        Throwable primary = failures.get(0);
        for (int i = 1; i < failures.size(); i++) {
            primary.addSuppressed(failures.get(i));
        }
        if (primary instanceof RuntimeException) {
            return (RuntimeException) primary;
        }
        if (primary instanceof Error) {
            throw (Error) primary;
        }
        return new IllegalStateException("datasource close failed", primary);
    }
}
