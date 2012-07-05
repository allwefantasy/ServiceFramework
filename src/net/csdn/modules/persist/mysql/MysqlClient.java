package net.csdn.modules.persist.mysql;

import com.google.inject.Inject;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.settings.Settings;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;

import static net.csdn.common.collections.WowCollections.join;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-1
 * Time: 下午9:09
 */
public class MysqlClient {

    private DataSource dataSource = null;
    private static Map<String, MysqlClient> mysqlManagers = new HashMap<String, MysqlClient>();
    private DataSourceManager dataSourceManager;

    private CSLogger logger = Loggers.getLogger(MysqlClient.class);

    private Settings settings;

    @Inject
    public MysqlClient(DataSourceManager _dataSourceManager, Settings _settings) {
        this.settings = _settings;
        for (Map.Entry<String, DataSource> entry : _dataSourceManager.dataSourceMap().entrySet()) {
            mysqlManagers.put(entry.getKey(), new MysqlClient(_dataSourceManager, settings, entry.getValue()));
        }

    }

    private MysqlClient(DataSourceManager dataSourceManager, Settings settings, DataSource _dataSource) {
        this.settings = settings;
        this.dataSource = _dataSource;
        this.dataSourceManager = dataSourceManager;
    }

    public MysqlClient mysqlService(String dataSourceName) {
        return mysqlManagers.get(dataSourceName);
    }

    public MysqlClient defaultMysqlService() {
        return mysqlManagers.get("mysql");
    }

    public DataSource dataSource() {
        return dataSource;
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void execute(String sql) {
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        try {
            conn = getConnection();
            preparedStatement = conn.prepareStatement(sql);
            preparedStatement.execute();
        } catch (SQLException e) {
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

    public void execute(String sql, Object... params) {
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        try {
            conn = getConnection();
            preparedStatement = conn.prepareStatement(sql);
            setParams(preparedStatement, params);
            preparedStatement.execute();
        } catch (SQLException e) {
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

    public <T> Set<T> projection_query_as_set(String sql, final String columnName, Object... objs) {
        return (Set<T>) defaultMysqlService().executeQuery(sql, new MysqlClient.SqlCallback() {
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

    public <T> List<T> projection_query(String sql, final String columnName, Object... objs) {
        return (List<T>) defaultMysqlService().executeQuery(sql, new MysqlClient.SqlCallback() {
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
        return (List<Map>) defaultMysqlService().executeQuery(sql, new MysqlClient.SqlCallback() {
            @Override
            public Object execute(ResultSet rs) {
                return MysqlClient.rsToMaps(rs);
            }
        }, objs);
    }

    public Map single_query(String sql, Object... objs) {
        return (Map) defaultMysqlService().executeQuery(sql, new MysqlClient.SqlCallback() {
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
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        long time1 = System.currentTimeMillis();
        try {
            conn = getConnection();
            preparedStatement = conn.prepareStatement(sql);
            setParams(preparedStatement, params);
            resultSet = preparedStatement.executeQuery();
            return callback.execute(resultSet);
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
                logger.info(sql + "  [" + join(params, ",") + "]");
            }

        }
        return null;
    }

    public <T> T executeQuery(String sql, SqlCallback<T> callback) {
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;
        long time1 = System.currentTimeMillis();
        try {
            conn = getConnection();
            preparedStatement = conn.prepareStatement(sql);
            resultSet = preparedStatement.executeQuery();
            return callback.execute(resultSet);
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
