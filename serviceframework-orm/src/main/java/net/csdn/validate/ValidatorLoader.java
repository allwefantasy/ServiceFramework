package net.csdn.validate;
import net.csdn.jpa.model.JPABase;

import java.util.HashMap;
import java.util.Map;

public class ValidatorLoader {

    public void load() throws Exception {
        Map<String, String> defaultMaps = new HashMap<String, String>();
        defaultMaps.put("format", "net.csdn.validate.impl.Format");
        defaultMaps.put("numericality", "net.csdn.validate.impl.Numericality");
        defaultMaps.put("presence", "net.csdn.validate.impl.Presence");
        defaultMaps.put("uniqueness", "net.csdn.validate.impl.Uniqueness");
        defaultMaps.put("length", "net.csdn.validate.impl.Length");
        defaultMaps.put("associated", "net.csdn.validate.impl.Associated");
        for (Map.Entry<String, String> entry : defaultMaps.entrySet()) {
            JPABase.validateParses.add(Class.forName(entry.getValue()).newInstance());
        }

    }
}