package com.example.controller.admin;

import com.example.controller.ApplicationController;
import com.example.model.Tag;
import com.example.model.TagGroup;
import com.example.model.TagSynonym;
import net.csdn.annotation.filter.BeforeFilter;
import net.csdn.annotation.rest.At;

import java.util.Map;

import static net.csdn.filter.FilterHelper.BeforeFilter.only;
import static net.csdn.modules.http.RestRequest.Method.DELETE;
import static net.csdn.modules.http.RestRequest.Method.POST;
import static net.csdn.modules.http.RestRequest.Method.PUT;
import static net.csdn.modules.http.support.HttpStatus.HTTP_400;

/**
 * 主要包括 Tag,TagGroup,TagSynonym  增删改查
 */
public class TagAdminController extends ApplicationController {

    @BeforeFilter
    private final static Map $find_tag = map(only, list("add_tag_to_tag_group", "destroy_tag","destroy_tag_from_tag_group"));

    @BeforeFilter
    private final static Map $find_tag_group = map(only, list("add_tag_to_tag_group", "destroy_tag_from_tag_group", "destroy_tag_group"));

    @BeforeFilter
    private final static Map $find_synonym = map(only, list("destroy_tag_from_tag_synonym", "add_tag_to_tag_synonym", "destroy_tag_synonym"));


    @At(path = "/tag_group", types = POST)
    public void create_tag_group() {
        TagGroup tagGroup = TagGroup.create(params());
        if (!tagGroup.save()) {
            render(HTTP_400, tagGroup.validateResults);
        }

        render(ok());
    }



    @At(path = "/tag_group", types = DELETE)
    public void destroy_tag_group() {
        tag_group.delete();
        render(ok());
    }


    @At(path = "/tag_group/tag", types = {PUT, POST})
    public void add_tag_to_tag_group() {
        tag_group.associate("tags").add(tag);
        render(ok());
    }

    @At(path = "/tag_group/tag", types = {DELETE})
    public void destroy_tag_from_tag_group() {
        tag_group.associate("tags").remove(tag);
        render(ok());
    }

    @At(path = "/tag_synonym/tag", types = DELETE)
    public void destroy_tag_from_tag_synonym() {
        tag_synonym.tags().remove(tag);
    }

    @At(path = "/tag_synonym/tag", types = POST)
    public void add_tag_to_tag_synonym() {
        tag_synonym.tags().add(tag);
        render(ok());
    }

    @At(path = "/tag_synonym", types = POST)
    public void create_tag_synonym() {
        TagSynonym tagSynonym = TagSynonym.create(params());
        if (!tagSynonym.save()) {
            render(HTTP_400, tagSynonym.validateResults);
        }
        render(ok());
    }

    @At(path = "/tag_synonym", types = DELETE)
    public void destroy_tag_synonym() {
        tag_synonym.delete();
        render(ok());
    }


    @At(path = "/tag", types = DELETE)
    public void destroy_tag() {
        tag.delete();
        render(ok());
    }

    @At(path = "/tag", types = {POST, PUT})
    public void create_tag() {
        Tag tag = Tag.create(params());
        if (!tag.save()) {
            render(HTTP_400, tag.validateResults);
        }
        render(ok());
    }


    private Tag tag;
    private TagGroup tag_group;
    private TagSynonym tag_synonym;

    private void find_tag() {
        tag = Tag.where("name=:name", map("name", param("tag_name"))).single_fetch();
        if (tag == null) {
            render(HTTP_400, fail("必须传递tag参数"));
        }
    }

    private void find_tag_group() {
        tag_group = TagGroup.where("name=:name", map("name", param("tag_group_name"))).single_fetch();
        if (tag_group == null) {
            render(HTTP_400, fail("必须传递tag_group参数"));
        }
    }

    private void find_synonym() {
        tag_synonym = TagSynonym.where("name=:name", map("name", param("tag_synonym_name"))).single_fetch();
        if (tag_synonym == null) {
            render(HTTP_400, fail("必须传递tag_synonym 名称参数"));
        }
    }
}
