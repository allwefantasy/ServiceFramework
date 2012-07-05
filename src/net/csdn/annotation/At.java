package net.csdn.annotation;

import net.csdn.modules.http.RestRequest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-6-7
 * Time: 下午4:15
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface At {
    RestRequest.Method[] types();

    String[] path() default {};
}
