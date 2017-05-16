package net.csdn.modules.http;

import net.sf.json.JsonConfig;
import net.sf.json.processors.JsonValueProcessor;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * User: WilliamZhu
 * Date: 12-7-5
 * Time: 下午7:17
 */
public class DateJsonValueProcessor implements JsonValueProcessor {

    private SimpleDateFormat simpleDateFormat;

    public DateJsonValueProcessor(String format) {
        simpleDateFormat = new SimpleDateFormat(format);
    }

    public DateJsonValueProcessor() {
        simpleDateFormat = new SimpleDateFormat("yyyyMMddhh");
    }

    @Override
    public Object processArrayValue(Object o, JsonConfig jsonConfig) {
        return o;
    }

    @Override
    public Object processObjectValue(String s, Object o, JsonConfig jsonConfig) {
        if (o instanceof Date) {
            return simpleDateFormat.format((Date) o);
        }
        return o;
    }
}
