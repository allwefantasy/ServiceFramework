package net.csdn.validate.impl;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.validate.BaseValidateParse;
import net.csdn.validate.ValidateHelper;
import net.csdn.validate.ValidateResult;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * User: WilliamZhu
 * Date: 12-7-3
 * Time: 下午6:22
 */
public class PresentParse extends BaseValidateParse {
    private CSLogger logger = Loggers.getLogger(getClass());

    public void parse(Object target, List<ValidateResult> validateResultList) {
        Class clzz = target.getClass();
        List<Field> fields = getValidateFields(clzz);
        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName().substring(1);
            try {
                Map prensentNote = (Map) ((Map) field.get(null)).get(ValidateHelper.presence);
                if (prensentNote == null) return;
                String message = (String) prensentNote.get("message");
                Object value = getModelField(clzz, fieldName).get(target);
                if (value instanceof String) {
                    if (value == null || ((String) value).isEmpty()) {
                        validateResultList.add(new ValidateResult(format(message, fieldName), fieldName));
                    }
                } else if (value == null) {
                    validateResultList.add(new ValidateResult(format(message, fieldName), fieldName));
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


}
