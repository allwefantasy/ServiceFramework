package net.csdn.jpa.ormfixture.shop;

import net.csdn.jpa.model.Model;

import javax.persistence.Table;

@Table(name = "sf_orm_bcr_customer")
public class Customer extends Model {
    private Integer id;
    private String name;
}
