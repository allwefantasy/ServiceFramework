package net.csdn.jpa.query.fixture;

import net.csdn.jpa.query.GenerateQueries;
import net.csdn.jpa.query.QueryMethod;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Transient;

/**
 * Shared query declaration for later ServiceFramework ORM database tests.
 * Compiling this type does not execute SQL. Generate {@code OrderEntityQueries}
 * with {@code -processor net.csdn.jpa.query.ServiceFrameworkQueryProcessor}
 * and {@code -processorpath} pointing at the ORM classes. There is no
 * {@code META-INF/services} processor registration.
 */
@Entity
@GenerateQueries({
        @QueryMethod(name = "findByStatusAndTenant", fields = {"status", "tenantId"}, orderBy = {"id"}),
        @QueryMethod(name = "findByRegion", fields = {"region"}, orderBy = {"id desc", "status"}),
        @QueryMethod(name = "findByWarehouse", fields = {"warehouse"})
})
public class OrderEntity extends OrderEntityBase {

    @Id
    private Long id;
    private String status;
    private Long tenantId;
    private String region;
    @Transient
    private String debugNote;

    public Long getId() {
        return id;
    }

    public String getStatus() {
        return status;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public String getRegion() {
        return region;
    }
}
