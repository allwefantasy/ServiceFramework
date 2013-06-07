package net.csdn.modules.thrift.util;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.mongo.Document;
import net.sf.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.csdn.common.reflect.ReflectHelper.field;

/**
 * 6/6/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class PojoCopy {

    private static CSLogger logger = Loggers.getLogger(PojoCopy.class);

    public static void copyProperties(Object from, Object to) {
        //it's easy to build a document from map,so we just convert from to map.
        if (to instanceof Document) {
            thriftPojoToDocument(from, (Document) to);
            return;
        }

        //document to pojo.
        if (from instanceof Document) {
            documentToThriftPojo(((Document) from), to);
            return;
        }

        //pojo(or map) to pojo
        for (Field field : to.getClass().getFields()) {
            String name = field.getName();
            try {

                Object value;

                if (from instanceof Map) {
                    value = ((Map) from).get(name);
                } else {
                    value = field(from, name);
                }

                if (isBasicType(value)) {
                    field(to, name, value);
                } else if (value instanceof List) {
                    ListProcess(from, to, name, value);
                } else if (value instanceof Map) {
                    MapProcess(from, to, name, value);
                } else if (value instanceof Set) {

                } else {
                    Object newDoc = field(to, name).getClass().newInstance();
                    copyProperties(value, newDoc);
                    field(to, name, newDoc);
                }

            } catch (Exception e) {
                //e.printStackTrace();
                logger.info(e.getMessage());
            }
        }
    }

    public static void thriftPojoToDocument(Object obj, Document doc) {
        doc.attributes().putAll(JSONObject.fromObject(obj));
        doc.copyAllAttributesToPojoFields();
    }

    private static void ListProcess(Object from, Object to, String key, Object value) throws Exception {
        List tempValue = (List) value;
        List newTempValue = (List) value.getClass().newInstance();
        for (Object temp : tempValue) {
            if (isBasicType(temp)) {
                newTempValue.add(temp);
            } else {
                Class newDocClass = (Class) ((ParameterizedType) (to.getClass().getField(key).getGenericType())).getActualTypeArguments()[0];
                Object newDoc = newDocClass.newInstance();
                copyProperties(temp, newDoc);
                newTempValue.add(newDoc);
            }

        }
        field(to, key, newTempValue);
    }

    private static void MapProcess(Object from, Object to, String key, Object value) throws Exception {
        Class newDocClass = to.getClass().getField(key).getType();
        Object newDoc = newDocClass.newInstance();
        copyProperties(value, newDoc);
        field(to, key, newDoc);
    }

    public static void documentToThriftPojo(Document doc, Object to) {
        Map<String, Object> keyValues = doc.attributes();
        copyProperties(keyValues, to);
    }


    private static boolean isBasicType(Object value) {
        return (value instanceof String ||
                value instanceof Integer ||
                value instanceof Double ||
                value instanceof Long ||
                value instanceof Float
        );
    }

}
