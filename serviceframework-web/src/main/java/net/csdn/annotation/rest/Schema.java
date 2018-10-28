package net.csdn.annotation.rest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Schema {
    String type();

    String format();

    String description();

    Class implementation() default EmptySchema.class;
}
