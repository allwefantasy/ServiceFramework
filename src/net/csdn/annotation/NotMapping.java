package net.csdn.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-4
 * Time: 下午4:38
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NotMapping {
    String[] value() default {};
}
