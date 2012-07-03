package net.csdn.validate;

import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-7-3
 * Time: 上午8:20
 */
public interface ValidateParse {
    public void parse(Object target, List<ValidateResult> validateResultList);
}
