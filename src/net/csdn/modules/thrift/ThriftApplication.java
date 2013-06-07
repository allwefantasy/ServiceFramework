package net.csdn.modules.thrift;

import net.csdn.ServiceFramwork;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.time.NumberExtendedForTime;
import net.sf.json.JSONArray;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 5/27/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class ThriftApplication {
    //各种工具方法
    public static <T> Set<T> newHashSet(T... arrays) {
        return WowCollections.newHashSet(arrays);
    }


    //@see  selectMap
    public static Map selectMap(Map map, String... keys) {
        return WowCollections.selectMap(map, keys);
    }

    public static Map paramByKeys(Map map, String... keys) {
        return WowCollections.selectMap(map, keys);
    }

    //@see aliasParamKeys
    public static Map selectMapWithAliasName(Map map, String... keys) {
        return WowCollections.selectMapWithAliasName(map, keys);
    }

    public static Map aliasParamKeys(Map params, String... keys) {
        return WowCollections.selectMapWithAliasName(params, keys);
    }

    public static Map map(Object... arrays) {
        return WowCollections.map(arrays);
    }


    public static <T> List<T> list(T... arrays) {
        return WowCollections.list(arrays);
    }

    //@see project
    public static <T> List<T> projectionColumn(List<Map> maps, String column) {
        return WowCollections.projectionColumn(maps, column);
    }

    public static List project(List<Map> list, String key) {
        return WowCollections.project(list, key);
    }

    public static String join(Collection collection, String split) {
        return WowCollections.join(collection, split);
    }

    public static String join(Collection collection) {
        return WowCollections.join(collection);
    }

    public static List projectByMethod(List list, String method, Object... params) {
        return WowCollections.projectByMethod(list, method, params);
    }

    public static Map double_list_to_map(List keys, List values) {
        return WowCollections.double_list_to_map(keys, values);
    }

    public static String join(Collection collection, String split, String wrapper) {
        return WowCollections.join(collection, split, wrapper);
    }

    public static String join(Object[] collection, String split, String wrapper) {
        return WowCollections.join(collection, split, wrapper);
    }

    public static String getString(Map map, String key) {
        return WowCollections.getString(map, key);
    }

    public static String getStringNoNull(Map map, String key) {
        return WowCollections.getStringNoNull(map, key);
    }

    public static Date getDate(Map map, String key) {
        return WowCollections.getDate(map, key);
    }

    public static long getDateAsLong(Map map, String key) {
        return WowCollections.getDateAsLong(map, key);
    }

    public static int getInt(Map map, String key) {
        return WowCollections.getInt(map, key);
    }

    public static long getLong(Map map, String key) {
        return WowCollections.getLong(map, key);
    }

    public static Set hashSet(Object[] array) {
        return WowCollections.hashSet(array);
    }


    public static List toList(Object[] array) {
        return WowCollections.toList(array);
    }


    public static Set hashSet(int[] array) {
        return WowCollections.hashSet(array);
    }

    public static List jsonArrayToList(JSONArray jsonArray) {
        return toList(jsonArray.toArray());

    }

    public static String join(Object[] arrays, String split) {
        return WowCollections.join(arrays, split);
    }

    public static String join(int[] arrays, String split) {

        return WowCollections.join(arrays, split);
    }

    public static <T> T or(T a, T b) {
        if (a == null) {
            return b;
        }
        return a;
    }

    public static Map selectMapWithAliasNameInclude(Map map, String... keys) {
        return WowCollections.selectMapWithAliasNameInclude(map, keys);
    }

    public static Pattern RegEx(String reg) {
        return Pattern.compile(reg);
    }

    public static Pattern regEx(String reg) {
        return RegEx(reg);
    }

    public boolean isNull(Object key) {
        return key == null;
    }


    public <T> T service(Class<T> clzz) {
        return ServiceFramwork.injector.getInstance(clzz);
    }

    //时间扩展
    public NumberExtendedForTime time(int number) {
        return new NumberExtendedForTime(number);
    }

    //时间扩展
    public NumberExtendedForTime time(long number) {
        return new NumberExtendedForTime(number);
    }

}
