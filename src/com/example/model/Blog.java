package com.example.model;

import net.csdn.annotation.Scope;
import net.csdn.annotation.Validate;
import net.csdn.jpa.model.JPQL;
import net.csdn.jpa.model.Model;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.validate.ValidateHelper.*;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-1
 * Time: 下午2:25
 */
@Entity
public class Blog extends Model {


    /*-------Named Scope-------*/
    @Scope
    public final static JPQL activeBlogs = where("status=:status", map("status", Status.active.value));

    /*-------help methods--------*/
    public enum Status {
        active(1), locked(0), draft(2), trashed(3), deleted(4);
        public int value;

        Status(int value) {
            this.value = value;
        }
    }

    /*-------validator--------*/
    @Validate
    private final static Map $title = map(presence, map("message", "{}字段不能为空"));


    /*-------associated--------*/

    @OneToMany(mappedBy = "blog")
    private List<BlogComment> blog_comments = new ArrayList<BlogComment>();


    @OneToOne(cascade = CascadeType.ALL)
    private BlogBody blog_body;

}
