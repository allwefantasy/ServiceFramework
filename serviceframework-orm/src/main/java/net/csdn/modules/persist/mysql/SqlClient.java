package net.csdn.modules.persist.mysql;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-1
 * Time: 下午9:09
 */
public class SqlClient {

    private DataSource dataSource = null;

    public SqlClient(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public DataSource dataSource() {
        return dataSource;
    }

    private Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }


    public void execute(String sql, Object... params) throws SQLException {
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        try {
            conn = getConnection();
            preparedStatement = preparedStatement(conn, sql, false);
            if (params.length > 0) {
                setParams(preparedStatement, params);
            }
            preparedStatement.execute();
        } finally {
            try {
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                //ignore
            }

        }

    }

    private PreparedStatement preparedStatement(Connection conn, String sql, boolean streaming) throws SQLException {
        if (streaming) {
            PreparedStatement preparedStatement = conn.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            preparedStatement.setFetchSize(1000);
        }
        return conn.prepareStatement(sql);
    }

    /*
      遍历表使用
     */
    public void executeStreaming(String sql, Object... params) throws SQLException {
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        try {
            conn = getConnection();
            preparedStatement = preparedStatement(conn, sql, true);
            if (params.length > 0) {
                setParams(preparedStatement, params);
            }

            preparedStatement.execute();
        } finally {
            try {
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                //ignore
            }

        }

    }


    public void executeBatch(String sql, BatchSqlCallback callback) throws SQLException {
        Connection conn = null;
        PreparedStatement preparedStatement = null;
        long time1 = System.currentTimeMillis();
        try {
            conn = getConnection();
            preparedStatement = conn.prepareStatement(sql);
            callback.execute(preparedStatement);
            preparedStatement.executeBatch();
        } finally {
            try {
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                //ignore
            }
        }

    }


    public <T> T executeQuery(String sql, SqlCallback<T> callback, Object... params) throws SQLException {
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
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                //ignore
            }
            long time2 = System.currentTimeMillis();

        }
    }


    public <T> T executeStreamingQuery(String sql, SqlCallback<T> callback, Object... params) throws SQLException {
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
        } finally {
            try {
                if (resultSet != null) resultSet.close();
                if (preparedStatement != null) preparedStatement.close();
                if (conn != null) conn.close();
            } catch (SQLException e) {
                //ignore
            }
            long time2 = System.currentTimeMillis();

        }

    }


    public static Map rsToMapSingle(ResultSet rs, String[] keys) throws SQLException {
        boolean haveNext = rs.next();
        if (!haveNext) {
            return null;
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
