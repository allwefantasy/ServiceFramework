package com.example.model;

import net.csdn.jpa.model.Model;

import javax.persistence.Entity;
import javax.persistence.OneToOne;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-5
 * Time: 上午8:03
 */
@Entity
public class BlogBody extends Model {

    @OneToOne(mappedBy = "blog_body")
    private Blog blog;
}
