package net.csdn.mongo.validate.impl;

import net.csdn.mongo.validate.BaseValidateParse;
import net.csdn.mongo.validate.ValidateHelper;
import net.csdn.mongo.validate.ValidateResult;

import java.util.List;
import java.util.Map;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-3
 * Time: 上午8:21
 */
public class Length extends BaseValidateParse {
    private static String notice = "{} length is not allowed";

    @Override
    public void parse(final Object target, final List<ValidateResult> validateResultList) {
        final Class clzz = target.getClass();
        iterateValidateInfo(clzz, ValidateHelper.length, new ValidateIterator() {
            @Override
            public void iterate(String targetFieldName, Object info) throws Exception {
                String msg = notice;
                if (info instanceof Map) msg = messageWithDefault((Map) info, notice);
                Map length = (Map) info;
                Integer minimum = (Integer) length.get(ValidateHelper.Length.minimum);
                Integer maximum = (Integer) length.get(ValidateHelper.Length.maximum);
                String value = getModelFieldValue(target, targetFieldName) + "";
                if (value == null || value.isEmpty()) {
                    if (minimum != null) {
                        String too_short_msg = (String) length.get(ValidateHelper.Length.too_short);
                        msg = too_short_msg == null ? msg : too_short_msg;
                        validateResultList.add(validateResult(msg, targetFieldName));
                    }

                } else {
                    if (minimum != null) {
                        if (value.length() < minimum) {
                            String too_short_msg = (String) length.get(ValidateHelper.Length.too_short);
                            msg = too_short_msg == null ? msg : too_short_msg;
                            validateResultList.add(validateResult(msg, targetFieldName));
                        }
                    }

                    if (maximum != null && value.length() > maximum) {
                        String too_long_msg = (String) length.get(ValidateHelper.Length.too_long);
                        msg = too_long_msg == null ? msg : too_long_msg;
                        validateResultList.add(validateResult(msg, targetFieldName));
                    }
                }

            }
        });
    }
}
