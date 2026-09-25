package net.csdn.jpa.query;

/**
 * Structural view of a model. {@link #findField(String)} returns the nearest
 * declared field, including an ineligible field that shadows a superclass field.
 * Walking stops before {@code net.csdn.jpa.model.Model}, {@code JPABase} and
 * {@code java.lang.Object}.
 */
interface QueryModelView {

    String getModelName();

    QueryFieldView findField(String name);
}
