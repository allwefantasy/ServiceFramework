package test.com.example.controller;

import com.example.controller.tag.TagController;
import com.example.model.BlogTag;
import com.example.model.Tag;
import com.example.model.TagGroup;
import com.example.model.TagSynonym;
import net.csdn.exception.RenderFinish;
import net.csdn.junit.IocTest;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.RestResponse;
import net.sf.json.JSONObject;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.common.logging.support.MessageFormat.format;
import static org.junit.Assert.assertTrue;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午9:38
 */
public class TagControllerTest extends IocTest {


    @Test
    public void testSave() throws Exception {
        TagController tagController = injector.getInstance(TagController.class);

        tagController.mockRequest(map(
                "object_id", "17",
                "tags", "java,google"

        ), RestRequest.Method.PUT, null);

        //过滤器
        tagController.m("check_params");


        try {
            tagController.save();
        } catch (RenderFinish e) {

        }

        RestResponse restResponse = tagController.mockResponse();
        JSONObject renderResult = JSONObject.fromObject((String) restResponse.originContent());
        assertTrue(renderResult.getBoolean("ok"));

        dbCommit();
        List<BlogTag> blogTags = BlogTag.where("object_id=17").fetch();
        assertTrue(blogTags.size() == 2);

        //清理数据
        Tag.delete(format("name in ({})", "'java','google'"));
        BlogTag.delete("object_id=17");

    }


    //@Test
    public void testSearch() throws Exception {

        //准备一些数据
        prepareData();

        TagController tagController = injector.getInstance(TagController.class);
        tagController.mockRequest(map(
                "tags", "java,google",
                "channelIds", "1,2,3",
                "blockedTagsNames", "google",
                "orderFields", "created_at"
        ), RestRequest.Method.POST, null);

        //过滤器
        tagController.m("checkParam");

        try {
            tagController.search();
        } catch (RenderFinish e) {

        }

        RestResponse restResponse = tagController.mockResponse();
        Map renderResult = (Map) restResponse.originContent();
        //Assert.assertTrue(renderResult.get("total")==1);
    }

    public void prepareData() {
        //  List<Map> content = list(map("name","java,google","blog_tags",17,"created_at","2007022711")) ;
        //创建tag和关联表
        BlogTag.deleteAll();
        Tag.deleteAll();
        TagSynonym.deleteAll();

        BlogTag blogTag = BlogTag.create(map("object_id", 17, "created_at", 2007022711l));
        blogTag.attr("tag", Tag.create(map("name", "java")));
        blogTag.save();

        blogTag = BlogTag.create(map("object_id", 17, "created_at", 2007022711l));
        blogTag.attr("tag", Tag.create(map("name", "google")));
        blogTag.save();

        //添加一个同义词组
        TagSynonym tagSynonym = TagSynonym.create(map("name", "java"));

        List<Tag> tags = Tag.where("name in ('java','google')").fetch();
        for (Tag tag : tags) {
            tagSynonym.attr("tags", List.class).add(tag);
            tag.attr("tag_synonym", tagSynonym);
        }
        tagSynonym.save();

        //添加一个组
        TagGroup tagGroup = TagGroup.create(map("name", "天才组"));
        for (Tag tag : tags) {
            tagGroup.attr("tags", List.class).add(tag);
            tag.attr("tag_groups", List.class).add(tagGroup);
        }
        tagGroup.save();

    }


}
