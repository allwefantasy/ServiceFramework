package com.example.model;

import net.csdn.jpa.model.Model;

import javax.persistence.Entity;
import javax.persistence.OneToOne;

/**
 * User: WilliamZhu
 * Date: 12-8-7
 * Time: 上午8:30
 */
@Entity
public class TagWiki extends Model {
    @OneToOne
    private Tag tag;
}
