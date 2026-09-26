package net.csdn.jpa.type.impl;

import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.EnumMemberValue;
import net.csdn.common.collect.Tuple;
import net.csdn.jpa.type.DBType;

import javax.persistence.Temporal;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;

/**
 * PostgreSQL type names after {@link net.csdn.jpa.type.DBInfo} normalization.
 */
public class PostgresType implements DBType {
    private final static Map<String, String> typeToJava = map(
            "CHAR".toLowerCase(), "String",
            "BPCHAR".toLowerCase(), "String",
            "VARCHAR".toLowerCase(), "String",
            "TEXT".toLowerCase(), "String",
            "BOOLEAN".toLowerCase(), "Boolean",
            "INT".toLowerCase(), "Integer",
            "SMALLINT".toLowerCase(), "Integer",
            "BIGINT".toLowerCase(), "Long",
            "FLOAT".toLowerCase(), "Float",
            "DOUBLE".toLowerCase(), "Double",
            "NUMERIC".toLowerCase(), "java.math.BigDecimal",
            "DECIMAL".toLowerCase(), "java.math.BigDecimal",
            "DATE".toLowerCase(), "java.util.Date",
            "TIMESTAMP".toLowerCase(), "java.util.Date",
            "TIMESTAMPTZ".toLowerCase(), "java.util.Date",
            "BYTEA".toLowerCase(), "byte[]",
            "UUID".toLowerCase(), "java.util.UUID"
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
        if (type.equals("TIMESTAMP") || type.equals("TIMESTAMPTZ")) {
            emb.setValue("TIMESTAMP");
            return new Tuple<Class, Map>(Temporal.class, map("value", emb));
        }
        return null;
    }
}
