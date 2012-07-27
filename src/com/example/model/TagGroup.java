package com.example.model;

import net.csdn.jpa.model.Model;

import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:54
 */
@Entity
public class TagGroup extends Model {
    @ManyToMany
    private List<Tag> tags = new ArrayList<Tag>();
}
