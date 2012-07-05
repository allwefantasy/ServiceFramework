package net.csdn.jpa.type;

import com.google.inject.ImplementedBy;
import com.google.inject.Singleton;
import javassist.bytecode.ConstPool;
import net.csdn.common.collect.Tuple;
import net.csdn.jpa.type.impl.MysqlType;

import java.sql.Connection;
import java.sql.ResultSetMetaData;
import java.util.Map;


/**
 * User: WilliamZhu
 * Date: 12-7-5
 * Time: 下午8:15
 */
public interface DBType {


    public Tuple<String, String> typeToJava(String sqlType);

    public Tuple<Class, Map> dateType(String type, ConstPool constPool);

    public Tuple<ResultSetMetaData, Connection> metaData(String entitySimpleName) throws Exception;
}
