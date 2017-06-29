package net.csdn.mongo.validate;

import java.lang.reflect.Field;
import java.util.List;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-3
 * Time: 上午8:20
 */
public interface ValidateParse {
    public void parse(final Object target, final List<ValidateResult> validateResultList);

    interface ValidateIterator {
        public void iterate(String targetFieldName, Object info) throws Exception;
    }
}
