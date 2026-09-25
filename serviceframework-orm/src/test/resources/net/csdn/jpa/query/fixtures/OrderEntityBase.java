package net.csdn.jpa.query.fixture;

/**
 * Superclass for the shared companion-query fixture.
 * {@code status} is an Integer here and is shadowed by {@code OrderEntity.status}.
 * A later ORM database test can compile this declaration with the query processor.
 * This file does not open a database connection.
 */
public class OrderEntityBase {

    private Integer status;
    private String warehouse;
    public final String locked = "locked";
    private transient String scratch;

    public Integer getInheritedStatus() {
        return status;
    }

    public String getWarehouse() {
        return warehouse;
    }
}
