package test.com.example.model;

import com.example.model.Tag;
import com.example.model.TagGroup;
import com.example.model.TagSynonym;
import net.csdn.junit.IocTest;
import org.junit.Test;

import java.util.List;

import static net.csdn.common.collections.WowCollections.map;
import static org.junit.Assert.assertTrue;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午5:01
 */
public class TagTest extends IocTest {


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

        List<TagSynonym> tagSynonyms = TagSynonym.where("name=:name", map("name", "java")).joins("left join fetch tags").fetch();
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
    public void manyToOneOrOneToMany() {
        String tagName = "jack";
        String tagSynonymName = "wowo";
        Tag tag = Tag.create(map("name", tagName));
        tag.save();
        dbCommit();

        tag = Tag.findById(tag.id());

        tag.associate("tag_synonym").set(TagSynonym.create(map("name", tagSynonymName)));
        dbCommit();

        TagSynonym tagSynonym = TagSynonym.where("name=:name", map("name", tagSynonymName)).single_fetch();
        assertTrue(tagSynonym != null);

        tag = Tag.findById(tag.id());
        TagSynonym.delete("name=?", tagSynonymName);
        tag.delete();
    }

}
