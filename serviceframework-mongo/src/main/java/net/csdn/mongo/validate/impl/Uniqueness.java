package net.csdn.mongo.validate.impl;

import net.csdn.mongo.Criteria;
import net.csdn.mongo.validate.BaseValidateParse;
import net.csdn.mongo.validate.ValidateHelper;
import net.csdn.mongo.validate.ValidateParse;
import net.csdn.mongo.validate.ValidateResult;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-4
 * Time: 上午7:10
 */
public class Uniqueness extends BaseValidateParse {
    private static String notice = "{} is not uniq";

    @Override
    public void parse(final Object target, final List<ValidateResult> validateResultList) {
        try {
            final Class clzz = target.getClass();
            iterateValidateInfo(clzz, ValidateHelper.uniqueness, new ValidateIterator() {
                @Override
                public void iterate(String targetFieldName, Object info) throws Exception {
                    String msg = notice;
                    if (info instanceof Map) msg = messageWithDefault((Map) info, notice);
                    Object value = getModelFieldValue(target, targetFieldName);

                    List models = ((Criteria) clzz.getDeclaredMethod("where", String.class, Map.class).invoke(null,map(targetFieldName, value))).fetch();
                    if (models.size() > 0) {
                        validateResultList.add(validateResult(msg, targetFieldName));
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
