package net.csdn.jpa.type.impl;

import com.google.inject.Inject;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.EnumMemberValue;
import net.csdn.common.collect.Tuple;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.type.DBType;

import javax.persistence.Temporal;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;

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
            "MEDIUMTEXT", "String",
            "LONGTEXT", "String",
            "BIT", "Boolean",
            "BOOLEAN", "Boolean",
            "VARCHAR", "String",
            "Text", "String",
            "INT", "Integer",
            "SMALLINT", "Integer",
            "TINYINT", "Integer",
            "BIGINT", "Long",
            "FlOAT", "Float",
            "DOUBLE", "Double",
            "DATE", "java.util.Date",
            "DATETIME", "java.util.Date",
            "TIMESTAMP", "java.util.Date"

    );

    public Tuple<String, String> typeToJava(String sqlType) {
        String type = typeToJava.get(sqlType);
        if (type == null) type = "byte[]";
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

}
