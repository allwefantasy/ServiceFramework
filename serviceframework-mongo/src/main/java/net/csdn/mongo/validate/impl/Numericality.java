package net.csdn.mongo.validate.impl;

import net.csdn.mongo.validate.BaseValidateParse;
import net.csdn.mongo.validate.ValidateHelper;
import net.csdn.mongo.validate.ValidateResult;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-3
 * Time: 下午8:12
 */
public class Numericality extends BaseValidateParse {

    private static String notice = "{} is not a valid numeric";

    @Override
    public void parse(final Object target, final List<ValidateResult> validateResultList) {
        final Class clzz = target.getClass();
        iterateValidateInfo(clzz, ValidateHelper.numericality, new ValidateIterator() {
            @Override
            public void iterate(String targetFieldName, Object info) throws Exception {

                String msg = notice;
                if (info instanceof Map) msg = messageWithDefault((Map) info, notice);
                Map<String, Object> numInfo = (Map) info;
                Comparable value = (Comparable) getModelFieldValue(target, targetFieldName);
                if (value instanceof String) {
                    validateResultList.add(validateResult(msg, targetFieldName));
                    return;
                }
                numInfo.remove("message");
                for (String key : numInfo.keySet()) {
                    if (key.equals(ValidateHelper.Numericality.odd) || key.equals(ValidateHelper.Numericality.even)) {
                        if (!oddOrEven(key, value)) {
                            validateResultList.add(validateResult(msg, targetFieldName));
                        }
                    } else {
                        Comparable double2 = (Comparable) numInfo.get(key);
                        if (!numericCompare(key, value, double2)) {
                            validateResultList.add(validateResult(msg, targetFieldName));
                        }
                    }
                }
            }
        });
    }

    private boolean oddOrEven(String method, Comparable value) {
        try {
            return (Boolean) ValidateHelper.Numericality.class.getDeclaredMethod(method, Integer.class).invoke(null, (Integer) value);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private boolean numericCompare(String method, Comparable d1, Comparable d2) {
        try {
            return (Boolean) ValidateHelper.Numericality.class.getDeclaredMethod(method, Comparable.class, Comparable.class).invoke(null, d1, d2);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

}
