package net.csdn.annotation;

import javax.inject.Singleton;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-1
 * Time: 上午7:47
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Util {
    public Class value() default Singleton.class;
}
