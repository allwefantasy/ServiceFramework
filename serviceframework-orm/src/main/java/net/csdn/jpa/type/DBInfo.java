package net.csdn.jpa.type;

import com.google.inject.Inject;
import net.csdn.common.Strings;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-7-25
 * Time: 下午7:53
 */
public class DBInfo {
    public final List<String> tableNames = new ArrayList<String>();
    public final Map<String, Map<String, String>> tableColumns = new HashMap<String, Map<String, String>>();

    private Settings settings;

    @Inject
    public DBInfo(Settings settings) {
        this.settings = settings;
        try {
            boolean disableMysql = settings.getAsBoolean(JPA.mode() + ".datasources.mysql.disable", false);
            if (!disableMysql) {
                info();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void info() throws Exception {
        Connection conn = null;
        Class.forName("com.mysql.jdbc.Driver").newInstance();
        Map<String, Settings> groups = settings.getGroups(JPA.mode() + ".datasources");
        Settings mysqlSetting = groups.get("mysql");
        if (mysqlSetting == null) {
            return;
        }

        Map<String, String>  properties = JPA.properties(mysqlSetting);

        conn = DriverManager.getConnection(properties.get("url"), properties.get("username"), properties.get("password"));

        DatabaseMetaData databaseMetaData = conn.getMetaData();
        ResultSet resultSet = databaseMetaData.getTables(null, null, "%", null);
        while (resultSet.next()) {
            String tableName = resultSet.getString(3);
            tableNames.add(tableName);
            PreparedStatement ps = conn.prepareStatement("select * from " + tableName + " limit 1");
            ResultSet rs = ps.executeQuery();
            ResultSetMetaData rsme = rs.getMetaData();
            int columnCount = rsme.getColumnCount();
            Map<String, String> columns = new HashMap<String, String>();
            for (int i = 1; i <= columnCount; i++) {
                String fieldName = rsme.getColumnName(i);
                String typeName = rsme.getColumnTypeName(i);
                columns.put(fieldName, typeName);

            }
            tableColumns.put(tableName, columns);
            tableColumns.put(Strings.toCamelCase(tableName, true), columns);
        }
        conn.close();
    }

}
