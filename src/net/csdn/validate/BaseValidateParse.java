package net.csdn.validate;

import net.csdn.annotation.Validate;

import javax.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.csdn.common.logging.support.MessageFormat.format;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-3
 * Time: 下午7:18
 */
public abstract class BaseValidateParse implements ValidateParse {
    protected List<Field> getValidateFields(Class clzz) {
        List<Field> validateFields = new ArrayList<Field>();
        Field[] fields = clzz.getDeclaredFields();
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers()) || !Modifier.isPrivate(field.getModifiers()))
                continue;
            if (field.getAnnotation(Validate.class) == null) continue;
            if (field.getName().startsWith("$")) continue;
            validateFields.add(field);
        }

        return validateFields;
    }

    protected ValidateResult validateResult(String msg, String targetFieldName) {
        return new ValidateResult(format(msg, targetFieldName), targetFieldName);
    }

    protected String messageWithDefault(Map info, String message) {
        String temp = (String) info.get(ValidateHelper.message);
        return temp == null ? message : temp;
    }

    protected Field getModelField(Class clzz, String name) {
        try {
            Field field = clzz.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    protected void iterateValidateInfo(Class clzz, String target, ValidateIterator validateIterator) {
        try {
            List<Field> fields = getValidateFields(clzz);
            for (Field field : fields) {
                field.setAccessible(true);
                Map info = (Map) ((Map) field.get(null));
                if (info == null) continue;
                if (info.get(target) == null) continue;
                Object wow = info.get(target);
                validateIterator.iterate(field.getName().substring(1), field, wow);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected List<Field> getModelFields(Class clzz) {
        List<Field> modelFields = new ArrayList<Field>();
        Field[] fields = clzz.getFields();
        for (Field field : fields) {
            if (Modifier.isStatic(field.getModifiers()) || Modifier.isPrivate(field.getModifiers())) continue;
            if (field.getAnnotation(Transient.class) != null) continue;
            modelFields.add(field);
        }
        return modelFields;
    }
}
