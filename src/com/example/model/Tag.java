package com.example.model;

import net.csdn.annotation.Validate;
import net.csdn.jpa.model.Model;

import javax.persistence.*;
import java.util.*;

import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.validate.ValidateHelper.presence;
import static net.csdn.validate.ValidateHelper.uniqueness;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午4:52
 */
@Entity
public class Tag extends Model {
    @Validate
    private final static Map $name = map(presence, map("message", "{}字段不能为空"), uniqueness, map("message", "{}字段不能重复"));


    @ManyToOne
    private TagSynonym tag_synonym;

    @OneToMany
    private List<BlogTag> blog_tags = new ArrayList<BlogTag>();


    @ManyToMany
    private List<TagGroup> tag_groups = new ArrayList<TagGroup>();


    public static Set<String> synonym(String wow_names) {
        String[] names = wow_names.split(",");

        Set<String> temp = new HashSet<String>();
        for (String name : names) {
            Tag tag = Tag.where("name=:name", map("name", name)).single_fetch();
            if (tag == null) continue;
            List<Tag> tags = Tag.where("tag_synonym=:tag_synonym", map("tag_synonym", tag.tag_synonym)).fetch();
            for (Tag tag1 : tags) {
                temp.add(tag1.attr("name", String.class));
            }

        }
        return temp;
    }

}
