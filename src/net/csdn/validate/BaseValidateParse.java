package net.csdn.validate;

import net.csdn.annotation.Validate;

import javax.persistence.Transient;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-7-3
 * Time: 下午7:18
 */
public abstract class BaseValidateParse implements ValidateParse {
    protected List<Field> getValidateFields(Class clzz) {
        List<Field> validateFields = new ArrayList<Field>();
        Field[] fields = clzz.getDeclaredFields();
        for (Field field : fields) {
            if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isPrivate(field.getModifiers())) continue;
            if (field.getAnnotation(Validate.class) == null) continue;
            validateFields.add(field);
        }

        return validateFields;
    }


    protected Field getModelField(Class clzz, String name) {
        try {
            return clzz.getField(name);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
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
