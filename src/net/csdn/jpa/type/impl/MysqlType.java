package net.csdn.jpa.type.impl;

import com.google.inject.Inject;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.EnumMemberValue;
import net.csdn.common.collect.Tuple;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.type.DBType;

import javax.persistence.Temporal;
import java.sql.*;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: WilliamZhu
 * Date: 12-7-5
 * Time: 下午7:53
 */

public class MysqlType implements DBType {


    @Inject
    private Settings settings;
    private final static Map<String, String> typeToJava = map(
            "CHAR", "String",
            "VARCHAR", "String",
            "Text", "String",
            "INT", "Integer",
            "BIGINT", "Long",
            "FlOAT", "Float",
            "DOUBLE", "Double",
            "DATE", "java.util.Date",
            "DATETIME", "java.util.Date",
            "TIMESTAMP", "java.util.Date"

    );

    public Tuple<String, String> typeToJava(String sqlType) {
        String type = typeToJava.get(sqlType);
        return new Tuple<String, String>(sqlType, type);
    }

    public Tuple<Class, Map> dateType(String type, ConstPool constPool) {
        EnumMemberValue emb = new EnumMemberValue(constPool);
        emb.setType("javax.persistence.TemporalType");
        if (type.equals("DATE")) {
            emb.setValue("DATE");
            return new Tuple<Class, Map>(Temporal.class, map("value", emb));
        }
        if (type.equals("DATETIME")) {
            emb.setValue("DATE");
            return new Tuple<Class, Map>(Temporal.class, map("value", emb));
        }
        if (type.equals("TIMESTAMP")) {
            emb.setValue("TIMESTAMP");
            return new Tuple<Class, Map>(Temporal.class, map("value", emb));
        }
        return null;
    }

    public Tuple<ResultSetMetaData, Connection> metaData(String entitySimpleName) throws Exception {
        Connection conn = null;
        Class.forName("com.mysql.jdbc.Driver").newInstance();
        Map<String, Settings> groups = settings.getGroups("datasources");
        Settings mysqlSetting = groups.get("mysql");
        String url = "jdbc:mysql://{}:{}/{}?useUnicode=true&characterEncoding=utf8";
        url = format(url, mysqlSetting.get("host", "127.0.0.1"), mysqlSetting.get("port", "3306"), mysqlSetting.get("database", "csdn_search_client"));
        conn = DriverManager.getConnection(url, mysqlSetting.get("username"), mysqlSetting.get("password"));
        PreparedStatement ps = conn.prepareStatement("select * from " + entitySimpleName + " limit 1");
        ResultSet rs = ps.executeQuery();
        return new Tuple<ResultSetMetaData, Connection>(rs.getMetaData(), conn);
    }
}
