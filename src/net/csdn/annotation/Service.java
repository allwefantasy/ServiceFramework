package net.csdn.annotation;

import com.google.inject.Singleton;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * User: WilliamZhu
 * Date: 12-7-1
 * Time: 上午7:44
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Service {
    public Class value() default Singleton.class;

    public Class implementedBy();
}
