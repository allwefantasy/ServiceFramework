package net.csdn.jpa.query;

/**
 * One resolved field. The processor implementation reads {@code javax.lang.model}
 * elements. The runtime implementation reads reflection. Neither copies a
 * second JPQL grammar.
 */
interface QueryFieldView {

    String getName();

    String getJavaType();

    boolean isPrimitive();

    boolean isEnum();

    /**
     * Same structural filter as the existing JPA dynamic finders:
     * instance, non-final, non-synthetic, and not {@code @Transient}.
     * Association mappings are also rejected here.
     */
    boolean isEligible();

    String getIneligibleReason();

    boolean isSupportedScalar();
}
