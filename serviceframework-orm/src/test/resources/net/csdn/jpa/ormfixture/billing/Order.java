package net.csdn.jpa.ormfixture.billing;

import net.csdn.jpa.model.Model;

import javax.persistence.Entity;
import javax.persistence.Table;

@Entity(name = "BillingOrder")
@Table(name = "sf_orm_bcr_bill_order")
public class Order extends Model {
    private Integer id;
    private String code;
}
