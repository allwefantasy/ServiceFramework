package net.csdn.modules.persist.mysql;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.OrmSession;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.csdn.common.collections.WowCollections.join;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-1
 * Time: 下午9:09
 */
public class MysqlClient {

    private static final MysqlClient CURRENT = new MysqlClient(true);

    private final boolean bridge;
    private final Map<String, MysqlClient> services;
    private DataSource dataSource = null;
    private DataSourceManager dataSourceManager;
    private CSLogger logger = Loggers.getLogger(MysqlClient.class);
    private Settings settings;

    /**
     * Bridge to the current application client. It does not retain a datasource.
     */
    public static MysqlClient currentBridge() {
        return CURRENT;
    }

    private MysqlClient(boolean bridge) {
        this.bridge = bridge;
        this.services = null;
    }

    public MysqlClient settings(Settings settings) {
        MysqlClient owner = facade();
        if (owner != this) {
            return owner.settings(settings);
        }
        this.settings = settings;
        return this;
    }

    public MysqlClient(DataSourceManager dataSourceManager, Settings settings) {
        this.bridge = false;
        this.settings = settings;
        this.dataSourceManager = dataSourceManager;
        this.services = new LinkedHashMap<String, MysqlClient>();
        for (Map.Entry<String, DataSource> entry : dataSourceManager.dataSourceMap().entrySet()) {
            this.services.put(entry.getKey(), new MysqlClient(dataSourceManager, settings, entry.getValue(), this.services));
        }
    }

    public MysqlClient(DataSourceManager dataSourceManager) {
        this(dataSourceManager, null);
    }

    public MysqlClient(DataSource dataSource) {
        this.bridge = false;
        this.dataSource = dataSource;
        this.services = null;
    }

    private MysqlClient(DataSourceManager dataSourceManager, Settings settings, DataSource dataSource, Map<String, MysqlClient> services) {
        this.bridge = false;
        this.settings = settings;
        this.dataSource = dataSource;
        this.dataSourceManager = dataSourceManager;
        this.services = services;
    }

    public MysqlClient mysqlService(String dataSourceName) {
        MysqlClient owner = facade();
        if (owner != this) {
            return owner.mysqlService(dataSourceName);
        }
        if (services == null) {
            return null;
        }
        return services.get(dataSourceName);
    }

    public void addNewMySQL(String name, Settings properties) {
        MysqlClient owner = facade();
        if (owner != this) {
            owner.addNewMySQL(name, properties);
            return;
        }
        if (dataSourceManager == null || services == null) {
            throw new IllegalStateException("mysql registry is not available");
        }
        DataSource ds = dataSourceManager.buildPool(properties);
        dataSourceManager.dataSourceMap().put(name, ds);
        synchronized (services) {
            services.put(name, new MysqlClient(dataSourceManager, settings, ds, services));
        }
    }

    public MysqlClient defaultMysqlService() {
        MysqlClient owner = facade();
        if (owner != this) {
            return owner.defaultMysqlService();
        }
        if (services == null) {
            return null;
        }
        return services.get("mysql");
    }

    public DataSource dataSource() {
        MysqlClient owner = facade();
        if (owner != this) {
            return owner.dataSource();
        }
        if (dataSource == null && services != null) {
            MysqlClient delegated = services.get("mysql");
            return delegated == null ? null : delegated.dataSource;
        }
        return dataSource;
    }

    private MysqlClient facade() {
        if (bridge) {
            return OrmSession.current().mysqlClient();
        }
        return this;
    }

    private MysqlClient executionTarget() {
        MysqlClient owner = facade();
        if (owner != this) {
            return owner.executionTarget();
        }
        if (dataSource == null && services != null) {
            MysqlClient delegated = services.get("mysql");
            if (delegated == null) {
                throw new IllegalStateException("default mysql datasource is not registered");
            }
            return delegated;
        }
        return this;
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void execute(String sql, Object... params) {
        MysqlClient owner = executionTarget();
        if (owner != this) {
            owner.execute(sql, params);
            return;
        }
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        try {
            conn = getConnection();
            preparedStatement = preparedStatement(conn, sql, false);
            if (params.length > 0) {
                setParams(preparedStatement, params);
            }
            preparedStatement.execute();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                //ignore
            }

        }

    }

    private PreparedStatement preparedStatement(Connection conn, String sql, boolean streaming) throws Exception {
        if (streaming) {
            PreparedStatement preparedStatement = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            preparedStatement.setFetchSize(1000);
        }
        return conn.prepareStatement(sql);
    }

    /*
      遍历表使用
     */
    public void executeStreaming(String sql, Object... params) {
        MysqlClient owner = executionTarget();
        if (owner != this) {
            owner.executeStreaming(sql, params);
            return;
        }
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        try {
            conn = getConnection();
            preparedStatement = preparedStatement(conn, sql, true);
            if (params.length > 0) {
                setParams(preparedStatement, params);
            }

            preparedStatement.execute();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                //ignore
            }

        }

    }


    public <T> Set<T> projectionByColumn(String sql, final String columnName, Object... objs) {
        MysqlClient owner = executionTarget();
        if (owner != this) {
            return owner.projectionByColumn(sql, columnName, objs);
        }
        return (Set<T>) this.executeQuery(sql, new SqlCallback() {
            @Override
            public Object execute(ResultSet rs) {
                Set<Object> temp = new HashSet<Object>();
                try {

                    while (rs.next()) {
                        temp.add(rs.getObject(columnName));
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                return temp;
            }
        }, objs);
    }

    public <T> List<T> projectionByColumn2(String sql, final String columnName, Object... objs) {
        MysqlClient owner = executionTarget();
        if (owner != this) {
            return owner.projectionByColumn2(sql, columnName, objs);
        }
        return (List<T>) this.executeQuery(sql, new SqlCallback() {
            @Override
            public Object execute(ResultSet rs) {
                List<Object> temp = new ArrayList<Object>();
                try {

                    while (rs.next()) {
                        temp.add(rs.getObject(columnName));
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                return temp;
            }
        }, objs);
    }

    public List<Map> query(String sql, Object... objs) {
        MysqlClient owner = executionTarget();
        if (owner != this) {
            return owner.query(sql, objs);
        }
        return (List<Map>) this.executeQuery(sql, new SqlCallback() {
            @Override
            public Object execute(ResultSet rs) {
                return MysqlClient.rsToMaps(rs);
            }
        }, objs);
    }

    public List<Map> streamingQuery(String sql, Object... objs) {
        MysqlClient owner = executionTarget();
        if (owner != this) {
            return owner.streamingQuery(sql, objs);
        }
        return (List<Map>) this.executeStreamingQuery(sql, new SqlCallback() {
            @Override
            public Object execute(ResultSet rs) {
                return MysqlClient.rsToMaps(rs);
            }
        }, objs);
    }

    public Map single_query(String sql, Object... objs) {
        MysqlClient owner = executionTarget();
        if (owner != this) {
            return owner.single_query(sql, objs);
        }
        return (Map) this.executeQuery(sql, new SqlCallback() {
            @Override
            public Object execute(ResultSet rs) {
                try {
                    return MysqlClient.rsToMapSingle(rs, MysqlClient.getRsCloumns(rs));
                } catch (SQLException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }, objs);
    }

    public void executeBatch(String sql, BatchSqlCallback callback) {
        MysqlClient owner = executionTarget();
        if (owner != this) {
            owner.executeBatch(sql, callback);
            return;
        }
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        long time1 = System.currentTimeMillis();
        try {
            conn = getConnection();
            preparedStatement = conn.prepareStatement(sql);
            callback.execute(preparedStatement);
            preparedStatement.executeBatch();
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                //ignore
            }
            if (this.settings.getAsBoolean("enable_sql_log", false)) {
                logger.info(" Load (" + (System.currentTimeMillis() - time1) + "ms)");
                logger.info(sql);
            }

        }

    }


    public Map executeQuerySingle(String sql, Object... params) {
        MysqlClient owner = executionTarget();
        if (owner != this) {
            return owner.executeQuerySingle(sql, params);
        }
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        long time1 = System.currentTimeMillis();
        try {
            conn = getConnection();
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            return rsToMapSingle(resultSet, getRsCloumns(resultSet));
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                //ignore
            }
            long time2 = System.currentTimeMillis();
            if (this.settings.getAsBoolean("enable_sql_log", false)) {
                logger.info(" Load (" + (time2 - time1) + "ms)");
                logger.info(sql);
            }

        }
        return null;
    }


    public <T> T executeQuery(String sql, SqlCallback<T> callback, Object... params) {
        MysqlClient owner = executionTarget();
        if (owner != this) {
            return owner.executeQuery(sql, callback, params);
        }
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        long time1 = System.currentTimeMillis();
        try {
            conn = getConnection();
            preparedStatement = preparedStatement(conn, sql, false);
            if (params.length > 0)
                setParams(preparedStatement, params);
            resultSet = preparedStatement.executeQuery();
            return callback.execute(resultSet);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                //ignore
            }
            long time2 = System.currentTimeMillis();
            if (this.settings.getAsBoolean("enable_sql_log", false)) {
                logger.info(" Load (" + (time2 - time1) + "ms) " + sql + "  [" + join(params, ",") + "]");
            }

        }
        return null;
    }


    public <T> T executeStreamingQuery(String sql, SqlCallback<T> callback, Object... params) {
        MysqlClient owner = executionTarget();
        if (owner != this) {
            return owner.executeStreamingQuery(sql, callback, params);
        }
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        long time1 = System.currentTimeMillis();
        try {
            conn = getConnection();
            preparedStatement = preparedStatement(conn, sql, true);
            if (params.length > 0)
                setParams(preparedStatement, params);
            resultSet = preparedStatement.executeQuery();
            return callback.execute(resultSet);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                //ignore
            }
            long time2 = System.currentTimeMillis();
            if (this.settings.getAsBoolean("enable_sql_log", false)) {
                logger.info(" Load (" + (time2 - time1) + "ms) " + sql + "  [" + join(params, ",") + "]");
            }

        }
        return null;
    }


    public static Map rsToMapSingle(ResultSet rs, String[] keys) throws SQLException {
        try {
            boolean haveNext = rs.next();
            if (!haveNext) {
                return null;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        String[] _keys = keys;
        if (_keys == null)
            _keys = getRsCloumns(rs);
        Map temp = rsToMap(rs, _keys);
        return temp;
    }

    public static Map rsToMap(ResultSet rs, String[] keys) {
        Map temp = new HashMap();
        for (int i = 0; i < keys.length; i++) {

            try {
                temp.put(keys[i], rs.getObject(keys[i]));
            } catch (SQLException e) {
                continue;
            }
        }
        return temp;
    }


    public static List<Map> rsToMaps(ResultSet rs, String[] keys) {
        List result = new ArrayList();
        try {
            while (rs.next()) {
                result.add(rsToMap(rs, keys));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }

    public static List<Map> rsToMaps(ResultSet rs) {
        List result = new ArrayList();
        try {
            while (rs.next()) {
                result.add(rsToMap(rs, getRsCloumns(rs)));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return result;
    }


    public static String[] getRsCloumns(ResultSet rs) throws SQLException {
        ResultSetMetaData rsm = rs.getMetaData();
        String[] columns = new String[rsm.getColumnCount()];
        for (int i = 0; i < columns.length; i++) {
            columns[i] = rsm.getColumnLabel(i + 1);
        }
        return columns;
    }

    private static void setParams(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null || params.length == 0)
            return;
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    public interface SqlCallback<T> {
        public T execute(ResultSet rs);
    }

    public interface BatchSqlCallback {
        public void execute(PreparedStatement ps);
    }

    public interface SqlListCallback<T> {
        public List<T> execute(ResultSet rs);
    }
}
