package net.csdn.common.collections;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import net.csdn.common.exception.ArgumentErrorException;
import net.csdn.common.reflect.ReflectHelper;

import java.sql.Timestamp;
import java.util.*;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-5-17
 * Time: 下午3:27
 */
public class WowCollections {

    public final static Map EMPTY_MAP = map();
    public final static List EMPTY_LIST = list();

    public static <T> Set<T> newHashSet(T... arrays) {
        Set<T> sets = new HashSet<T>(arrays.length);
        for (T t : arrays) {
            sets.add(t);
        }
        return sets;
    }


//    public static DBObject translateMapToDBObject(Map map) {
//        DBObject query = new BasicDBObject();
//        query.putAll(map);
//        return query;
//    }

    public static boolean isEmpty(Collection collection) {
        if (collection == null || collection.isEmpty()) {
            return true;
        }
        return false;
    }

    public static boolean isEmpty(String str) {
        if (str == null || str.isEmpty()) {
            return true;
        }
        return false;
    }

    public static boolean isNull(Object obj) {
        return obj == null;
    }

    public static Map selectMap(Map map, String... keys) {
        Map temp = new HashMap();
        for (String key : keys) {
            temp.put(key, map.get(key));
        }
        return temp;
    }

    public static Map selectMapWithAliasName(Map map, String... keys) {
        Map temp = new HashMap();
        temp.putAll(map);
        for (int i = 0; i < keys.length; i++) {
            String oldKey = keys[i];
            String newKey = keys[++i];
            if (map.containsKey(oldKey)) {
                temp.put(newKey, map.get(oldKey));
                temp.remove(oldKey);
            }
        }
        return temp;
    }

    public static Map selectMapWithAliasNameInclude(Map map, String... keys) {
        Map temp = new HashMap();
        for (int i = 0; i < keys.length; i++) {
            String oldKey = keys[i];
            String newKey = keys[++i];
            if (map.containsKey(oldKey)) {
                temp.put(newKey, map.get(oldKey));
            }

        }
        return temp;
    }


    public static Map map(Object... arrays) {
        Map maps = new HashMap();
        if (arrays.length % 2 != 0) throw new ArgumentErrorException("arrays 长度 必须为偶数");
        for (int i = 0; i < arrays.length; i++) {
            maps.put(arrays[i], arrays[++i]);
        }
        return maps;
    }


    public static <T> List<T> list(T... arrays) {
        List<T> list = new ArrayList<T>(arrays.length);
        for (T t : arrays) {
            list.add(t);
        }
        return list;
    }


    public static <T> List<T> projectionColumn(List<Map> maps, String column) {
        List<T> lists = new ArrayList<T>(maps.size());
        for (Map temp : maps) {
            lists.add((T) temp.get(column));
        }
        return lists;
    }


    public static String join(Collection collection, String split) {
        if (collection.size() == 0) return "";
        if (split.isEmpty()) {
            return join(collection);
        }
        Iterator ite = collection.iterator();
        StringBuffer stringBuffer = new StringBuffer();
        while (ite.hasNext()) {
            stringBuffer.append(ite.next() + split);
        }

        return stringBuffer.substring(0, stringBuffer.length() - split.length());
    }

    public static String join(Collection collection) {
        if (collection.size() == 0) return null;
        Iterator ite = collection.iterator();
        StringBuffer stringBuffer = new StringBuffer();
        while (ite.hasNext()) {
            stringBuffer.append(ite.next());
        }
        return stringBuffer.toString();
    }

    public static List<List> splitList(List list, int size) {
        return Lists.partition(list, size);
    }

    public static List project(List<Map> list, String key) {
        List list1 = new ArrayList(list.size());
        for (Map map : list) {
            list1.add(map.get(key));
        }
        return list1;
    }


    public static List projectByMethod(List list, String method, Object... params) {
        List list1 = new ArrayList(list.size());
        for (Object obj : list) {
            list1.add(ReflectHelper.method(obj, method, params));
        }
        return list1;
    }

    public static Map doubleListToMap(List keys, List values) {
        Map map = new HashMap();
        int keys_size = keys.size();
        int values_size = values.size();
        assert keys_size == values_size;
        for (int i = 0; i < keys_size; i++) {
            map.put(keys.get(i), values.get(i));
        }
        return map;
    }

    public static String join(Collection collection, String split, String wrapper) {
        if (collection.size() == 0) return null;
        Iterator ite = collection.iterator();
        StringBuffer stringBuffer = new StringBuffer();
        while (ite.hasNext()) {
            stringBuffer.append(wrapper + ite.next() + wrapper + split);
        }
        stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        return stringBuffer.toString();
    }

    public static String join(Object[] collection, String split, String wrapper) {
        if (collection.length == 0) return null;
        StringBuffer stringBuffer = new StringBuffer();
        for (Object obj : collection) {
            stringBuffer.append(wrapper + obj + wrapper + split);
        }
        stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        return stringBuffer.toString();
    }

    public static String getString(Map map, String key) {
        return (String) map.get(key);
    }

    public static String getStringNoNull(Map map, String key) {
        String s = (String) map.get(key);
        if (s == null) return "";
        return s;
    }

    public static Date getDate(Map map, String key) {
        return new Date(((Timestamp) map.get(key)).getTime());
    }

    public static long getDateAsLong(Map map, String key) {
        return ((Timestamp) map.get(key)).getTime();
    }

    public static int getInt(Map map, String key) {
        return ((Integer) map.get(key)).intValue();
    }

    public static long getLong(Map map, String key) {
        return ((Long) map.get(key)).longValue();
    }

    public static Map getMap(Map map, String key) {
        return (Map) map.get(key);
    }

    public static Boolean getBoolean(Map map, String key) {
        return (Boolean) map.get(key);
    }

    public static Set hashSet(Object[] array) {
        Set sets = new HashSet();
        for (Object obj : array) {
            sets.add(obj);
        }
        return sets;
    }


    public static List toList(Object[] array) {
        List lists = new ArrayList();
        for (Object obj : array) {
            lists.add(obj);
        }
        return lists;
    }


    public static Set hashSet(int[] array) {
        Set sets = new HashSet();
        for (int obj : array) {
            sets.add(obj);
        }
        return sets;
    }


    public static String join(Object[] arrays, String split) {
        if (arrays == null || arrays.length == 0) return "";
        StringBuffer stringBuffer = new StringBuffer();
        for (Object obj : arrays) {
            stringBuffer.append(obj + split);
        }
        stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        return stringBuffer.toString();
    }

    public static String join(int[] arrays, String split) {

        StringBuffer stringBuffer = new StringBuffer();
        for (int obj : arrays) {
            stringBuffer.append(obj + split);
        }
        stringBuffer.deleteCharAt(stringBuffer.length() - 1);
        return stringBuffer.toString();
    }

    public static Iterable<String> split(String s, String seperator) {
        return Splitter.on(seperator).split(s);
    }

    public static List<String> split2(String s, String seperator) {
        if (isEmpty(s)) return Lists.newArrayList();
        return Lists.newArrayList(Splitter.on(seperator).split(s));
    }

    public static List<String> split2SkipEmpty(String s, String seperator) {
        if (isEmpty(s)) return Lists.newArrayList();
        List<String> list = Lists.newArrayList();
        for (String temp : Splitter.on(seperator).split(s)) {
            if (!isEmpty(temp)) {
                list.add(temp);
            }
        }
        return list;
    }

    public static List<Integer> split2IntoInt(String s, String seperator) {
        if (isEmpty(s)) return Lists.newArrayList();
        List<Integer> ids = Lists.newArrayList();
        for (String temp : Splitter.on(seperator).split(s)) {
            try {
                ids.add(Integer.parseInt(temp));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ids;
    }

    public static List<Integer> split2IntoDouble(String s, String seperator) {
        if (isEmpty(s)) return Lists.newArrayList();
        List ids = Lists.newArrayList();
        for (String temp : Splitter.on(seperator).split(s)) {
            try {
                ids.add(Double.parseDouble(temp));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ids;
    }

    public static List<Integer> split2IntoLong(String s, String seperator) {
        if (isEmpty(s)) return Lists.newArrayList();
        List ids = Lists.newArrayList();
        for (String temp : Splitter.on(seperator).split(s)) {
            try {
                ids.add(Long.parseLong(temp));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ids;
    }

    public static Iterable<String> splitWithRegex(String s, String seperatorPattern) {
        if (isEmpty(s)) return Lists.newArrayList();
        return Splitter.onPattern(seperatorPattern).split(s);
    }


    public static <K, V> List iterateMap(Map<K, V> map, MapIterator mapIterator) {
        List list = list();
        for (Map.Entry<K, V> entry : map.entrySet()) {
            Object t = mapIterator.iterate(entry.getKey(), entry.getValue());
            if (t != null)
                list.add(t);
        }
        return list;
    }

    public static <K, V> List iterate_map(Map<K, V> map, MapIterator mapIterator) {
        return iterateMap(map, mapIterator);
    }


    public static List subList(List temp, int size) {
        return temp.subList(0, size > temp.size() ? temp.size() : size);
    }

    public static List subList(List temp, int start, int size) {
        if (start > (temp.size() - 1)) return Lists.newArrayList();
        int tempSize = start + size;
        return temp.subList(start, tempSize > temp.size() ? temp.size() : tempSize);
    }

    public static void uniqList(List list) {

    }

    public static <K> List iterateList(List<K> list, ListIterator<K> listIterator) {
        List result = list();
        for (K obj : list) {
            Object t = listIterator.iterate(obj);
            if (t != null) {
                result.add(t);
            }
        }
        return result;
    }

    public static <K> List iterate_list(List<K> list, ListIterator<K> listIterator) {
        return iterateList(list, listIterator);
    }


    public interface MapIterator<K, V> {
        public Object iterate(K key, V value);
    }

    public interface ListIterator<K> {
        public Object iterate(K key);
    }

}
