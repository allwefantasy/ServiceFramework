package net.csdn.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-4
 * Time: 下午9:31
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Hint {
    public Class value();
}
