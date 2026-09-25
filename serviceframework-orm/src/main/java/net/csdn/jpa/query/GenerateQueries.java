package net.csdn.jpa.query;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Container for {@link QueryMethod}. Write it explicitly, or repeat
 * {@code @QueryMethod} and let the compiler wrap this container.
 * Runtime-visible so the declaration survives class loading.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GenerateQueries {

    QueryMethod[] value();
}
