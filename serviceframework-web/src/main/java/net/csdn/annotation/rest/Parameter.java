package net.csdn.annotation.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Parameter {
    String name();

    boolean required() default false;

    String description() default "";

    boolean allowEmptyValue() default true;

    boolean allowReserved() default false;

}
