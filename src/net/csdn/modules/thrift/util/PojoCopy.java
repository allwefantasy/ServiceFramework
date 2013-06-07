package net.csdn.modules.thrift.util;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import org.apache.thrift.TBase;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.csdn.common.reflect.ReflectHelper.field;

/**
 * 6/6/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class PojoCopy {

    private CSLogger logger = Loggers.getLogger(PojoCopy.class);

    public void copyProperties(Object from, Object doc) {
        for (Field field : doc.getClass().getFields()) {
            String name = field.getName();
            try {
                Object value = field(from, name);
                if (isBasicType(value)) {
                    field(doc, name, value);
                } else if (value instanceof List) {
                    List tempValue = (List) value;
                    List newTempValue = (List) value.getClass().newInstance();
                    for (Object temp : tempValue) {
                        if (isBasicType(temp)) {
                            newTempValue.add(temp);
                        } else {
                            Object newDoc = temp.getClass().newInstance();
                            copyProperties(temp, newDoc);
                            newTempValue.add(temp);
                        }

                    }
                    field(doc, name, value);
                } else if (value instanceof Map) {

                } else if (value instanceof Set) {

                } else if (value instanceof TBase) {
                    Object newDoc = field(doc, name).getClass().newInstance();
                    copyProperties(value, newDoc);
                    field(doc, name, value);
                } else {
                    logger.info("key[" + name + "] value[" + value + "] i do not how to process");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isBasicType(Object value) {
        return (value instanceof String ||
                value instanceof Integer ||
                value instanceof Double ||
                value instanceof Long ||
                value instanceof Float
        );
    }

}
