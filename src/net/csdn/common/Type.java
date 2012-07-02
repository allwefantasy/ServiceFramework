package net.csdn.common;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * User: william
 * Date: 11-9-14
 * Time: 下午4:45
 */
public class Type {
    public static void field(String name, double value) {


    }

    public static void field(String name, float value) {

    }

    public static void field(String name, int value) {

    }

    public static void field(String name, long value) {

    }

    public static void field(String name, short value) {

    }

    public static void field(String name, byte value) {

    }

    public static void field(String name, boolean value) {

    }


    public static void field(String name, double[] value) {

    }

    public static void field(String name, float[] value) {

    }

    public static void field(String name, int[] value) {

    }

    public static void field(String name, byte[] value) {

    }


    public static void field(String name, long[] value) {

    }

    public static void field(String name, Date value) {

    }

    public static void field(String name, Map value) {

    }

    public static void field(String name, List value) {

    }

    public static void field(String name, Object[] value) {

    }

    public static void field(String name, Object value) {
        Class type = value.getClass();
        if (type == String.class) {
            field(name, ((Float) value).floatValue());
        } else if (type == Float.class) {
            field(name, ((Float) value).floatValue());
        } else if (type == Double.class) {
            field(name, ((Double) value).doubleValue());
        } else if (type == Integer.class) {
            field(name, ((Integer) value).intValue());
        } else if (type == Long.class) {
            field(name, ((Long) value).longValue());
        } else if (type == Short.class) {
            field(name, ((Short) value).shortValue());
        } else if (type == Byte.class) {
            field(name, ((Byte) value).byteValue());
        } else if (type == Boolean.class) {
            field(name, ((Boolean) value).booleanValue());
        } else if (type == Date.class) {
            field(name, (Date) value);
        } else if (type == byte[].class) {
            field(name, (byte[]) value);
        } else if (value instanceof Map) {
            //noinspection unchecked
            field(name, (Map<String, Object>) value);
        } else if (value instanceof List) {
            field(name, (List) value);
        } else if (value instanceof Object[]) {
            field(name, (Object[]) value);
        } else if (value instanceof int[]) {
            field(name, (int[]) value);
        } else if (value instanceof long[]) {
            field(name, (long[]) value);
        } else if (value instanceof float[]) {
            field(name, (float[]) value);
        } else if (value instanceof double[]) {
            field(name, (double[]) value);
        } else {
            field(name, value.toString());
        }
    }
}
