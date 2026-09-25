package net.csdn.validate;

import net.csdn.common.enhancer.EnhancementFailure;
import net.csdn.jpa.JPA;
import net.csdn.jpa.model.JPABase;

import java.util.LinkedHashMap;
import java.util.Map;

public class ValidatorLoader {

    public void load() {
        Map<String, String> defaultMaps = new LinkedHashMap<String, String>();
        defaultMaps.put("format", "net.csdn.validate.impl.Format");
        defaultMaps.put("numericality", "net.csdn.validate.impl.Numericality");
        defaultMaps.put("presence", "net.csdn.validate.impl.Presence");
        defaultMaps.put("uniqueness", "net.csdn.validate.impl.Uniqueness");
        defaultMaps.put("length", "net.csdn.validate.impl.Length");
        defaultMaps.put("associated", "net.csdn.validate.impl.Associated");
        for (Map.Entry<String, String> entry : defaultMaps.entrySet()) {
            try {
                Class<?> type = Class.forName(entry.getValue(), true, JPA.classLoader());
                if (installed(type)) {
                    continue;
                }
                JPABase.validateParses.add(type.getDeclaredConstructor().newInstance());
            } catch (EnhancementFailure failure) {
                throw failure;
            } catch (Exception e) {
                throw new EnhancementFailure(
                        EnhancementFailure.Category.ENHANCEMENT,
                        entry.getValue(),
                        "validator",
                        "load",
                        "validator " + entry.getKey() + " was not loaded",
                        e);
            }
        }
    }

    private static boolean installed(Class<?> type) {
        for (int i = 0; i < JPABase.validateParses.size(); i++) {
            Object parse = JPABase.validateParses.get(i);
            if (parse != null && parse.getClass() == type) {
                return true;
            }
        }
        return false;
    }
}
