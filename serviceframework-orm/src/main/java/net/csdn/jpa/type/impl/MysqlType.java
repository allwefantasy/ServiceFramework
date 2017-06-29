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
            "CHAR".toLowerCase(), "String",
            "MEDIUMTEXT".toLowerCase(), "String",
            "LONGTEXT".toLowerCase(), "String",
            "BIT".toLowerCase(), "Boolean",
            "BOOLEAN".toLowerCase(), "Boolean",
            "VARCHAR".toLowerCase(), "String",
            "Text".toLowerCase(), "String",
            "INT".toLowerCase(), "Integer",
            "SMALLINT".toLowerCase(), "Integer",
            "TINYINT".toLowerCase(), "Integer",
            "BIGINT".toLowerCase(), "Long",
            "FlOAT".toLowerCase(), "Float",
            "DOUBLE".toLowerCase(), "Double",
            "DATE".toLowerCase(), "java.util.Date",
            "DATETIME".toLowerCase(), "java.util.Date",
            "TIMESTAMP".toLowerCase(), "java.util.Date"

    );

    public Tuple<String, String> typeToJava(String sqlType) {
        String type = typeToJava.get(sqlType.toLowerCase());
        if (type == null) type = "byte[]";
        return new Tuple<String, String>(sqlType, type);
    }

    public Tuple<Class, Map> dateType(String type, ConstPool constPool) {
        type = type.toUpperCase();
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
