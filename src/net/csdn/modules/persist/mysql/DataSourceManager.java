package net.csdn.modules.persist.mysql;

import com.google.inject.Inject;
import com.mchange.v2.c3p0.ComboPooledDataSource;
import net.csdn.common.settings.Settings;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: WilliamZhu
 * Date: 12-6-1
 * Time: 下午9:11
 */
public class DataSourceManager {

    private Map<String, DataSource> dataSourceMap;
    private Settings settings;


    @Inject
    public DataSourceManager(Settings _settings) {
        this.settings = _settings;
        dataSourceMap = buildDataSourceMap();
    }

    public DataSource datasource(String name) {
        return dataSourceMap.get(name);
    }

    public Map<String, DataSource> dataSourceMap() {
        return dataSourceMap;
    }

    //only for test
    public void buildDataSourceMap(String prefix) {
        dataSourceMap.clear();
        Map<String, Settings> groups = settings.getGroups(prefix);
        for (Map.Entry<String, Settings> group : groups.entrySet()) {
            dataSourceMap.put(group.getKey(), buildPool(group.getValue()));
        }
    }

    private Map<String, DataSource> buildDataSourceMap() {
        Map<String, DataSource> tempDataSourceMap = new HashMap<String, DataSource>();
        Map<String, Settings> groups = settings.getGroups("datasources");
        for (Map.Entry<String, Settings> group : groups.entrySet()) {
            tempDataSourceMap.put(group.getKey(), buildPool(group.getValue()));
        }
        return tempDataSourceMap;
    }

    private ComboPooledDataSource buildPool(Settings mysqlSetting) {
        String url = "jdbc:mysql://{}:{}/{}?useUnicode=true&characterEncoding=utf8";
        url = format(url, mysqlSetting.get("host", "127.0.0.1"), mysqlSetting.get("port", "3306"), mysqlSetting.get("database", "csdn_search_client"));
        try {
            ComboPooledDataSource dataSource = new ComboPooledDataSource();
            dataSource.setUser(mysqlSetting.get("username"));
            dataSource.setPassword(mysqlSetting.get("password"));
            dataSource.setJdbcUrl(url);
            dataSource.setDriverClass("com.mysql.jdbc.Driver");
            dataSource.setInitialPoolSize(5);
            dataSource.setMinPoolSize(5);
            dataSource.setMaxPoolSize(10);
            dataSource.setMaxStatements(50);
            dataSource.setMaxIdleTime(60);
            return dataSource;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    static {
        try {
            Class.forName("com.mysql.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

    }
}
