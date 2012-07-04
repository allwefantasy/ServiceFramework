package net.csdn.validate.impl;

import net.csdn.validate.BaseValidateParse;
import net.csdn.validate.ValidateHelper;
import net.csdn.validate.ValidateResult;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.csdn.common.collections.WowCollections.newArrayList;
import static net.csdn.common.collections.WowMaps.newHashMap;
import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.validate.ValidateHelper.Numericality.*;
import static net.csdn.validate.ValidateHelper.message;

/**
 * User: WilliamZhu
 * Date: 12-7-3
 * Time: 下午8:12
 */
public class NumericalityParse extends BaseValidateParse {

    //private Pattern pattern = Pattern.compile("\\A[+-]?\\d+\\Z");
    private static String notice = "{} is not a valid numeric";

    @Override
    public void parse(final Object target, final List<ValidateResult> validateResultList) {
        final Class clzz = target.getClass();

    }

    private Double doubleValue(Map info, String key) {
        Object obj = info.get(key);
        if (obj == null) return null;
        return Double.parseDouble(obj.toString());
    }
}
