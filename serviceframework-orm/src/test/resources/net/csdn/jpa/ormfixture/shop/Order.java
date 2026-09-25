package net.csdn.jpa.ormfixture.shop;

import net.csdn.jpa.model.Model;
import net.csdn.jpa.query.GenerateQueries;
import net.csdn.jpa.query.QueryMethod;

import javax.persistence.ManyToOne;
import javax.persistence.Table;

@GenerateQueries({
        @QueryMethod(name = "findByStatusAndRegion", fields = {"status", "region"}, orderBy = {"id"})
})
@Table(name = "sf_orm_bcr_shop_order")
public class Order extends Model {
    private Integer id;
    private String status;
    private String region;
    private String label;
    @ManyToOne
    private Customer customer;

    public void setLabel(String label) {
        this.label = label == null ? null : label.trim();
    }

    public void setLabel(String label, String suffix) {
        this.label = (label == null ? "" : label) + (suffix == null ? "" : suffix);
    }

    public String getLabel() {
        return label;
    }
}
