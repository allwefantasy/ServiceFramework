package net.csdn.modules.http;

import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import java.util.Map;

/**
 * BlogInfo: william
 * Date: 11-9-7
 * Time: 上午11:22
 */
public class JSONObjectUtils {


    public static JSONObject newJSONObject(Map map) {
        return JSONObject.fromObject(map);

    }

    public static boolean checkExists(JSONObject object, String key) {
        return object.containsKey(key);
    }

    public static int getInt(JSONObject obj, String key, int... default_value) {
        try {

            Object temp = obj.get(key);
            if (temp instanceof String) {
                return Integer.parseInt((String) temp);
            }
            return obj.getInt(key);
        } catch (Exception e) {
            return default_value.length == 0 ? -1 : default_value[0];
        }
    }

    public static JSONObject getJSONObject(JSONObject obj, String key, JSONObject... default_value) {
        try {
            JSONObject object = obj.getJSONObject(key);
            if (object == null || object.size() == 0) return default_value.length == 0 ? null : default_value[0];
            return object;
        } catch (Exception e) {
            return default_value.length == 0 ? null : default_value[0];
        }

    }


    public static JSONArray getJSONArray(JSONObject obj, String key, JSONArray... default_value) {
        try {
            JSONArray object = obj.getJSONArray(key);
            if (object == null) return default_value.length == 0 ? null : default_value[0];
            return object;
        } catch (Exception e) {
            return default_value.length == 0 ? null : default_value[0];
        }
    }


    public static float getFloat(JSONObject obj, String key, float... default_value) {
        try {
            Object temp = obj.get(key);
            if (temp instanceof String) {
                return Float.parseFloat((String) temp);
            }
            return ((Double) (obj.getDouble(key))).floatValue();
        } catch (Exception e) {
            return default_value.length == 0 ? -1 : default_value[0];
        }
    }

    public static double getDouble(JSONObject obj, String key, double... default_value) {
        try {
            Object temp = obj.get(key);
            if (temp instanceof String) {
                return Double.parseDouble((String) temp);
            }
            return (obj.getDouble(key));
        } catch (Exception e) {
            return default_value.length == 0 ? -1 : default_value[0];
        }
    }

    public static long getLong(JSONObject obj, String key, long... default_value) {
        try {
            Object temp = obj.get(key);
            if (temp instanceof String) {
                return Long.parseLong((String) temp);
            }
            return (obj.getLong(key));
        } catch (Exception e) {
            return default_value.length == 0 ? -1 : default_value[0];
        }
    }

    public static boolean getBoolean(JSONObject obj, String key, boolean... default_value) {
        try {
            return obj.getBoolean(key);
        } catch (Exception e) {
            return default_value.length == 0 ? false : default_value[0];
        }
    }

    public static String getString(JSONObject obj, String key, String... default_value) {
        try {
            return obj.getString(key);
        } catch (Exception e) {
            return default_value.length == 0 ? null : default_value[0];
        }
    }
}
