package net.csdn.bootstrap.lifecycle.web.db.orm;

import net.csdn.jpa.model.Model;

import javax.persistence.Table;

@Table(name = "sf_web_lifecycle_record")
public class WebRecord extends Model {
    private Integer id;
    private String label;
}
