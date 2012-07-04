package net.csdn.bootstrap.loader.impl;

import net.csdn.bootstrap.loader.Loader;
import net.csdn.common.settings.Settings;
import net.csdn.jpa.JPA;
import net.csdn.jpa.model.JPABase;

import java.util.HashMap;
import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-7-4
 * Time: 下午1:26
 */
public class ValidatorLoader implements Loader {

    @Override
    public void load(Settings settings) throws Exception {
        Map<String, String> defaultMaps = new HashMap<String, String>();
        defaultMaps.put("format", "net.csdn.validate.impl.Format");
        defaultMaps.put("numericality", "net.csdn.validate.impl.Numericality");
        defaultMaps.put("presence", "net.csdn.validate.impl.Presence");
        defaultMaps.put("uniqueness", "net.csdn.validate.impl.Uniqueness");
        defaultMaps.put("length", "net.csdn.validate.impl.Length");
        Map<String, String> validators = settings.getByPrefix("validator.").getAsMap();
        for (Map.Entry<String, String> entry : validators.entrySet()) {
            defaultMaps.put(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : defaultMaps.entrySet()) {
            JPABase.validateParses.add(Class.forName(entry.getValue()).newInstance());
        }

    }
}
