package net.csdn.mongo.validate.impl;

import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.mongo.validate.BaseValidateParse;
import net.csdn.mongo.validate.ValidateHelper;
import net.csdn.mongo.validate.ValidateParse;
import net.csdn.mongo.validate.ValidateResult;
import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-3
 * Time: 下午6:22
 */
public class Presence extends BaseValidateParse {
    private CSLogger logger = Loggers.getLogger(getClass());
    private static String notice = "{} should not null or empty";

    public void parse(final Object target, final List<ValidateResult> validateResultList) {
        final Class clzz = target.getClass();
        iterateValidateInfo(clzz, ValidateHelper.presence, new ValidateIterator() {
            @Override
            public void iterate(String targetFieldName, Object info) throws Exception {
                String msg = notice;
                if (info instanceof Map) msg = messageWithDefault((Map) info, notice);
                Object value = getModelFieldValue(target,targetFieldName);

                if (value instanceof String) {
                    if (StringUtils.isEmpty((String) value)) {
                        validateResultList.add(validateResult(msg, targetFieldName));
                    }
                } else {
                    if (value == null) {
                        validateResultList.add(validateResult(msg, targetFieldName));
                    }
                }
            }
        });
    }


}
