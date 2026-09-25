package net.csdn.jpa.query;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one companion query method on a top-level model type.
 * Retention is runtime so later enhancement rules can read the same declaration
 * {@link RuntimeQueryMetadata} validates.
 *
 * <p>{@code fields} is an AND of equality predicates, in parameter order.
 * {@code orderBy} tokens are {@code field}, {@code field asc} or {@code field desc}.
 * Each generated method exists twice: once taking the declared field parameters
 * plus {@code int offset, int limit} (EntityManager comes from the JPA context),
 * and once with a leading {@code javax.persistence.EntityManager} parameter.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(GenerateQueries.class)
public @interface QueryMethod {

    String name();

    String[] fields();

    String[] orderBy() default {};
}
