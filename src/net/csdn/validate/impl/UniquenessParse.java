package net.csdn.validate.impl;

import net.csdn.jpa.JPA;
import net.csdn.jpa.model.JPQL;
import net.csdn.validate.BaseValidateParse;
import net.csdn.validate.ValidateHelper;
import net.csdn.validate.ValidateResult;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.newHashMap;

/**
 * User: WilliamZhu
 * Date: 12-7-4
 * Time: 上午7:10
 */
public class UniquenessParse extends BaseValidateParse {
    private static String notice = "{} is not uniq";

    @Override
    public void parse(final Object target, final List<ValidateResult> validateResultList) {
        try {
            final Class clzz = target.getClass();
            iterateValidateInfo(clzz, ValidateHelper.uniqueness, new ValidateIterator() {
                @Override
                public void iterate(String targetFieldName, Field field, Object info) throws Exception {
                    String msg = notice;
                    if (info instanceof Map) msg = messageWithDefault((Map) info, notice);
                    Object value = clzz.getField(targetFieldName).get(target);
                    String whereCondition = targetFieldName + "=:hold";
                    List models = ((JPQL) clzz.getDeclaredMethod("where", String.class, Map.class).invoke(null, whereCondition, newHashMap("hold", value))).fetch();
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
