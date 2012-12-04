package test.com.example.model;

import net.csdn.common.reflect.ReflectHelper;
import net.csdn.junit.IocTest;
import org.junit.Assert;
import org.junit.Test;
import com.example.model.*;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;
import static org.junit.Assert.assertTrue;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午5:01
 */
public class TagTest extends IocTest {




    public void testInheritance() {
        Tag tag = Tag.create(map("name", "java"));
        tag.save();
        dbCommit();

        TagRelation tagRelation = BlogTag.create(map("object_id", 17, "tag", tag));
        tagRelation.save();

        tagRelation = NewsTag.create(map("object_id", 17, "tag", tag));
        tagRelation.save();
        dbCommit();

        Assert.assertTrue(BlogTag.all().fetch().size() == 1);
        Assert.assertTrue(TagRelation.all().fetch().size() == 2);

        List<BlogTag> blogTags = BlogTag.all().fetch();
        Assert.assertTrue(blogTags.get(0).attr("object_id", Integer.class) == 17);

        TagRelation.deleteAll();
        Tag.deleteAll();
    }


    @Test
    public void testSqlQuery() {
        System.out.print(Tag.class.getName());
        Tag tag = Tag.create(map("name", "java"));
        tag.save();
        dbCommit();
        List<Map> lists = Tag.nativeSqlClient().query("select * from Tag");
        assertTrue(lists.size() == 1);
        Tag.deleteAll();

    }

    @Test
    public void testSqlQuery2() {

        Tag.nativeSqlClient().execute("insert into tag(name) value('java')");
        List<Map> lists = Tag.nativeSqlClient().query("select * from Tag");
        assertTrue(lists.size() == 1);
        Tag.deleteAll();

    }


    @Test
    public void testOneToOne() {

        Tag tag = Tag.create(map("name", "java"));
        TagWiki tagWiki = TagWiki.create(map("content", "我是java说明"));
        tag.associate("tag_wiki").set(tagWiki);
        tag.save();
        dbCommit();
        assertTrue(tagWiki.attr("tag", Tag.class) != null);

        Tag.deleteAll();
        TagWiki.deleteAll();

    }

    @Test
    public void testFormatValidate() {

        setUpTagAndTagSynonymData();
        TagSynonym tagSynonym = TagSynonym.where("name=:name", map("name", "java")).single_fetch();
        List<Tag> list = (List<Tag>) ReflectHelper.method(tagSynonym, "getTags");
        System.out.println(list.size());

        tearDownTagAndTagSynonymData();

    }

    @Test
    public void testAssociatedValidate() {
        Tag tag = Tag.create(map("name", "java"));
        BlogTag blogTag = BlogTag.create(map("object_id", 1));
        tag.m("blog_tags", blogTag);
        assertTrue(tag.save() == false);
        assertTrue(tag.validateResults.get(0).getFieldName().equals("object_id"));

        tag.validateResults.clear();
        blogTag.validateResults.clear();

        blogTag.attr("object_id", 10);
        assertTrue(tag.save());

        Tag.delete("name='java'");
        BlogTag.delete("object_id=10");
    }


    @Test
    public void testUniquenessValidate() {
        Tag tag = Tag.create(map("name", "java"));
        assertTrue(tag.save());
        dbCommit();
        tag = Tag.create(map("name", "java"));
        assertTrue(tag.save() == false);
        assertTrue(tag.validateResults.get(0).getFieldName().equals("name"));
        assertTrue(tag.validateResults.get(0).getMessage().contains("重复"));

        Tag.delete("name='java'");

    }

    @Test
    public void testPresenceValidate() {
        Tag tag = Tag.create(map("name", ""));
        assertTrue(tag.save() == false);
        assertTrue(tag.validateResults.get(0).getFieldName().equals("name"));
        tag = Tag.create(map("name", null));
        assertTrue(tag.save() == false);
        assertTrue(tag.validateResults.get(0).getFieldName().equals("name"));
    }

    private void setUpTagAndTagSynonymData() {
        String tagSynonymName = "java";
        TagSynonym tagSynonym = TagSynonym.create(map("name", tagSynonymName));
        tagSynonym.save();
        for (int i = 0; i < 10; i++) {
            tagSynonym.associate("tags").add(Tag.create(map("name", "tag_" + i)));
        }

        dbCommit();
    }

    private void tearDownTagAndTagSynonymData() {
        TagSynonym.delete("name=?", "java");
        Tag.delete("name like 'tag_%'");

    }

    @Test
    public void associationJPQLTest() {
        setUpTagAndTagSynonymData();

        TagSynonym tagSynonym = TagSynonym.where("name=:name", map("name", "java")).single_fetch();
        List<Tag> tags = tagSynonym.associate("tags").where("name=:name", map("name", "tag_1")).limit(1).fetch();
        assertTrue(tags.size() == 1);

        tearDownTagAndTagSynonymData();
    }

    @Test
    public void joinTest() {
        setUpTagAndTagSynonymData();

        List<TagSynonym> tagSynonyms = TagSynonym.where("name=:name", map("name", "java")).joins("left join  tags").fetch();
        assertTrue(tagSynonyms.size() == 1);
        assertTrue(tagSynonyms.get(0).attr("tags", List.class).size() == 10);

        tearDownTagAndTagSynonymData();

    }

    @Test
    public void manyToManyAssociationTest() {

        String groupName = "天才组2";
        String tagName = "哦喔喔";

        TagGroup tagGroup = TagGroup.create(map("name", groupName));
        tagGroup.save();
        //提交
        dbCommit();

        tagGroup = TagGroup.findById(tagGroup.id());
        List<Tag> tags = tagGroup.attr("tags", List.class);
        assertTrue(tags.size() == 0);


        Tag tag = Tag.create(map("name", tagName));
        tagGroup.associate("tags").add(tag);

        dbCommit();

        tagGroup = TagGroup.where("name=:name", map("name", groupName)).single_fetch();
        tags = tagGroup.attr("tags", List.class);
        assertTrue(tags.size() == 1);
        assertTrue(tag.id() != null);

        tagGroup.associate("tags").remove(tag);
        dbCommit();

        tagGroup = TagGroup.where("name=:name", map("name", groupName)).single_fetch();
        tags = tagGroup.attr("tags", List.class);
        assertTrue(tags.size() == 0);

        tagGroup.delete();
        Tag.delete("name=?", tagName);

    }

    @Test
    public void OneToMany() {
        String tagName = "jack";
        String tagSynonymName = "wowo";

        Tag tag = Tag.create(map("name", tagName));
        tag.associate("tag_synonym").set(TagSynonym.create(map("name", tagSynonymName)));
        tag.save();
        dbCommit();

        TagSynonym tagSynonym = TagSynonym.where("name=:name", map("name", tagSynonymName)).single_fetch();
        assertTrue(tagSynonym != null);

        tag = Tag.where("name=:name", map("name", tagName)).single_fetch();
        assertTrue(tag != null);
        tagSynonym.delete();
        tag.delete();
    }

    @Test
    public void OneToMany2() {
        String tagName = "jack";
        String tagSynonymName = "wowo";

        Tag tag = Tag.create(map("name", tagName));
        tag.tag_synonym().set(TagSynonym.create(map("name", tagSynonymName)));

        tag.save();
        dbCommit();

        TagSynonym tagSynonym = TagSynonym.where("name=:name", map("name", tagSynonymName)).single_fetch();
        assertTrue(tagSynonym != null);

        tag = Tag.where("name=:name", map("name", tagName)).single_fetch();
        assertTrue(tag != null);
        tagSynonym.delete();
        tag.delete();
    }

    @Test
    public void manyToOne() {
        String tagName = "jack";
        String tagSynonymName = "wowo";

        TagSynonym tagSynonym = TagSynonym.create(map("name", tagSynonymName));
        Tag tag = Tag.create(map("name", tagName));
        tagSynonym.associate("tags").add(tag);
        tagSynonym.save();
        dbCommit();

        tagSynonym = TagSynonym.where("name=:name", map("name", tagSynonymName)).single_fetch();
        assertTrue(tagSynonym != null);

        tag = Tag.where("name=:name", map("name", tagName)).single_fetch();
        assertTrue(tag != null);
        tagSynonym.delete();
        tag.delete();
    }

    @Test
    public void manyToOne2() {
        String tagName = "jack";
        String tagSynonymName = "wowo";

        TagSynonym tagSynonym = TagSynonym.create(map("name", tagSynonymName));
        tagSynonym.save();
        dbCommit();

        Tag tag = Tag.create(map("name", tagName));
        tagSynonym.associate("tags").add(tag);
        tagSynonym.save();

        dbCommit();

        tagSynonym = TagSynonym.where("name=:name", map("name", tagSynonymName)).single_fetch();
        assertTrue(tagSynonym != null);

        tag = Tag.where("name=:name", map("name", tagName)).single_fetch();
        assertTrue(tag != null);
        tagSynonym.delete();
        tag.delete();
    }

}
