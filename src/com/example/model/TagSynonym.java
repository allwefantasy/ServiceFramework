package com.example.model;

import net.csdn.jpa.model.Model;
import org.hibernate.annotations.Cascade;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:54
 */
@Entity
public class TagSynonym extends Model {
    @OneToMany(mappedBy = "tag_synonym", cascade = CascadeType.PERSIST)
    private List<Tag> tags = new ArrayList<Tag>();
}
