package net.csdn.common.enhancer;

/**
 * Ids reserved for later module rules. Common does not register those rules and
 * does not depend on ORM, Mongo or Web.
 * <p>
 * Intended order: {@code entity-mapping} before both {@code orm-query} and
 * {@code association}. {@code mongo-document} and {@code controller-filter} are
 * single rules with no requires edge.
 */
public final class EnhancementRuleIds {

    public static final String ENTITY_MAPPING = "entity-mapping";
    public static final String ORM_QUERY = "orm-query";
    public static final String ASSOCIATION = "association";
    public static final String MONGO_DOCUMENT = "mongo-document";
    public static final String CONTROLLER_FILTER = "controller-filter";

    private EnhancementRuleIds() {
    }
}
