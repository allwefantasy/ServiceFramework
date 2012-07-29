package com.example.model;

import net.csdn.AutoGeneration;
import net.csdn.jpa.association.Association;
import net.csdn.jpa.model.Model;

import javax.persistence.Entity;
import javax.persistence.OneToMany;
import java.util.List;

import static net.csdn.common.collections.WowCollections.list;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:54
 */
@Entity
public class TagSynonym extends Model {
    @OneToMany
    private List<Tag> tags = list();

    public Association tags() {
        throw new AutoGeneration();
    }
}
