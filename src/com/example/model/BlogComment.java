package com.example.model;

import net.csdn.jpa.model.Model;

import javax.persistence.Entity;
import javax.persistence.ManyToOne;

/**
 * User: WilliamZhu
 * Date: 12-7-11
 * Time: 下午9:48
 */
@Entity
public class BlogComment extends Model {

    @ManyToOne
    private Blog blog;
}
