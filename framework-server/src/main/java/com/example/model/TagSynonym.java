package com.example.model;

import net.csdn.common.exception.AutoGeneration;
import net.csdn.jpa.association.Association;
import net.csdn.jpa.model.Model;

import javax.persistence.OneToMany;
import java.util.List;

import static net.csdn.common.collections.WowCollections.list;

/**
 * 7/22/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class TagSynonym extends Model {

    @OneToMany
    private List<Tag> tags = list();

    public Association tags() {
        throw new AutoGeneration();
    }
}
