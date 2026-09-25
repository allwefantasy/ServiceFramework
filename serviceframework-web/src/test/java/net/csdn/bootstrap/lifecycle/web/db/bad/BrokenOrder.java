package net.csdn.bootstrap.lifecycle.web.db.bad;

import net.csdn.jpa.model.Model;

import javax.persistence.ManyToOne;

public class BrokenOrder extends Model {
    private Integer id;

    @ManyToOne
    private NotAnEntity customer;
}
