package com.example.model;

import net.csdn.jpa.JPA;
import net.csdn.junit.IocTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.list;
import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午5:01
 */
public class TagTest extends IocTest {


    @Test
    public void createTag() {
        Tag tag = Tag.create(map("name", ""));
        Assert.assertTrue(!tag.valid());

        tag = Tag.create(map("name", "java"));
        Assert.assertTrue(tag.valid());
        tag.save();

        List<Tag> tags = Tag.where("name=:name", map("name", "java")).fetch();
        Assert.assertTrue(tags.size() == 1);
        Assert.assertTrue(tags.get(0).attr("name", String.class).equals("java"));

        int id = tag.attr("id", Integer.class);
        tag.delete();

        tag = Tag.findById(id);
        Assert.assertTrue(tag == null);

    }

    @Test
    public void prepareData() {
        //  List<Map> content = list(map("name","java,google","blog_tags",17,"created_at","2007022711")) ;
        //创建tag和关联表
        BlogTag.deleteAll();
        Tag.deleteAll();
        TagSynonym.deleteAll();
        TagGroup.deleteAll();

        BlogTag blogTag = BlogTag.create(map("object_id", 17, "created_at", 2007022711l));
        blogTag.m("tag", Tag.create(map("name", "java")));
        blogTag.save();

        blogTag = BlogTag.create(map("object_id", 17, "created_at", 2007022711l));
        blogTag.m("tag", Tag.create(map("name", "google")));
        blogTag.save();

        //添加一个同义词组
        TagSynonym tagSynonym = TagSynonym.create(map("name", "java"));

        List<Tag> tags = Tag.where("name in ('java','google')").fetch();
        for (Tag tag : tags) {
            tagSynonym.m("tags", tag);
        }
        tagSynonym.save();

        //添加一个组
        TagGroup tagGroup = TagGroup.create(map("name", "天才组2"));
        for (Tag tag : tags) {
            tagGroup.m("tags", tag);
        }
        tagGroup.save();

    }


}
