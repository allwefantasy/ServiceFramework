package net.csdn.mongo.validate.impl;

import net.csdn.mongo.Document;
import net.csdn.mongo.validate.BaseValidateParse;
import net.csdn.mongo.validate.ValidateHelper;
import net.csdn.mongo.validate.ValidateParse;
import net.csdn.mongo.validate.ValidateResult;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-5
 * Time: 上午7:38
 */
public class Associated extends BaseValidateParse {
    @Override
    public void parse(final Object target, final List<ValidateResult> validateResultList) {
        final Class clzz = target.getClass();
        iterateValidateInfo(clzz, ValidateHelper.associated, new ValidateIterator() {
            @Override
            public void iterate(String targetFieldName, Object info) throws Exception {
                List<String> listFields = (List) info;
                for (String wow : listFields) {
                    Field objField = target.getClass().getDeclaredField(wow);
                    objField.setAccessible(true);
                    Object obj = objField.get(target);
                    if (obj instanceof Collection) {
                        Collection objs = (Collection) obj;
                        Iterator iterator = objs.iterator();
                        while (iterator.hasNext()) {
                            Document member = (Document) iterator.next();
                            if (member == null) continue;
                            if (!member.valid()) {
                                validateResultList.addAll(member.validateResults);
                            }

                        }
                    } else/*单个对象*/ {
                        Document member = (Document) obj;
                        if (member == null) continue;
                        if (!member.valid()) {
                            validateResultList.addAll(member.validateResults);
                        }
                    }
                }
            }
        });
    }
}
