package com.example.model;

import net.csdn.annotation.Validate;
import net.csdn.jpa.model.JPQL;
import net.csdn.jpa.model.Model;
import net.csdn.validate.ValidateHelper;

import javax.persistence.Entity;
import javax.persistence.ManyToMany;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.csdn.common.collections.WowCollections.join;
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

    @OneToMany(mappedBy = "tag")
    private List<BlogTag> blog_tags = new ArrayList<BlogTag>();

    @ManyToMany
    private List<TagGroup> tag_groups = new ArrayList<TagGroup>();


    public static String synonym(String wow_names) {
        String[] names = wow_names.split(",");
        //可以改为Set?
        List<String> temp = new ArrayList<String>();
        for (String name : names) {
            Tag tag = Tag.where("name=:name", map("name", name)).single_fetch();
            if (tag == null) continue;
            List<Tag> tags = Tag.where("tag_synonym=:tag_synonym",map("tag_synonym",tag.tag_synonym)).fetch();
            for (Tag tag1 : tags) {
                String tagName = tag1.attr("name", String.class);
                if (!temp.contains(tagName))
                    temp.add(tagName);
            }

        }
        return join(temp, ",", "'");
    }

}
