package net.csdn.validate.impl;

import net.csdn.validate.BaseValidateParse;
import net.csdn.validate.ValidateHelper;
import net.csdn.validate.ValidateParse;
import net.csdn.validate.ValidateResult;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * User: WilliamZhu
 * Date: 12-7-3
 * Time: 上午8:21
 */
public class LengthParse extends BaseValidateParse {
    private static String notice = "{} length is not allowed";

    @Override
    public void parse(final Object target, final List<ValidateResult> validateResultList) {
        final Class clzz = target.getClass();
        iterateValidateInfo(clzz, ValidateHelper.length, new ValidateIterator() {
            @Override
            public void iterate(String targetFieldName, Field field, Object info) throws Exception {
                String msg = notice;
                Map length = (Map) info;
                Integer minimum = (Integer) length.get(ValidateHelper.Length.minimum);
                Integer maximum = (Integer) length.get(ValidateHelper.Length.maximum);
                String value = (String) getModelField(clzz, targetFieldName).get(target);
                if (minimum != null) {
                    if (value == null || value.isEmpty()) {
                        String too_short_msg = (String) length.get(ValidateHelper.Length.too_short);
                        msg = too_short_msg == null ? notice : too_short_msg;
                        validateResultList.add(validateResult(msg, targetFieldName));
                    } else if (value.length() < minimum) {
                        String too_short_msg = (String) length.get(ValidateHelper.Length.too_short);
                        msg = too_short_msg == null ? notice : too_short_msg;
                        validateResultList.add(validateResult(msg, targetFieldName));
                    }
                }

                if (maximum != null && value.length() > maximum) {
                    String too_long_msg = (String) length.get(ValidateHelper.Length.too_long);
                    msg = too_long_msg == null ? notice : too_long_msg;
                    validateResultList.add(validateResult(msg, targetFieldName));
                }

            }
        });
    }
}
