package com.example.controller.tag;

import com.example.controller.ApplicationController;
import com.example.model.BlogTag;
import com.example.model.Tag;
import com.example.model.TagGroup;
import com.example.service.tag.RemoteDataService;
import com.google.inject.Inject;
import net.csdn.annotation.At;
import net.csdn.annotation.BeforeFilter;
import net.csdn.jpa.model.JPQL;
import net.csdn.jpa.model.Model;
import net.csdn.reflect.ReflectHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.filter.FilterHelper.BeforeFilter.only;
import static net.csdn.modules.http.RestRequest.Method.*;
import static net.csdn.modules.http.support.HttpStatus.HTTP_400;


public class TagController extends ApplicationController {

    @BeforeFilter
    private final static Map $checkParam = map(only, list("save", "search"));
    @BeforeFilter
    private final static Map $findTag = map(only, list("addTagToTagGroup", "deleteTagToTagGroup","createBlogTag"));


    @At(path = "/tag_group/create", types = POST)
    public void createTagGroup() {
        TagGroup tagGroup = TagGroup.create(params());
        if (!tagGroup.save()) {
            render(HTTP_400, tagGroup.validateResults);
        }
        render(OK);
    }


    @At(path = "/tag_group/tag", types = {PUT, POST})
    public void addTagToTagGroup() {
        TagGroup tagGroup = TagGroup.findById(paramAsInt("id"));
        tagGroup.associate("tags").add(tag);
        render(OK);
    }

    @At(path = "/tag_group/tag", types = {DELETE})
    public void deleteTagToTagGroup() {
        TagGroup tagGroup = TagGroup.findById(paramAsInt("id"));
        tagGroup.associate("tags").remove(tag);
        tagGroup.save();
        render(OK);
    }


    @At(path = "/{tag}/blog_tags", types = PUT)
    public void createBlogTag() {
        tag.associate("blog_tags").add(BlogTag.create(map("object_id", paramAsInt("object_id"))));
        render(OK);
    }


    /**
     * 通过插入doc 增加tag和doc的关联表
     * <p/>
     * type
     * doc所属的类型.example 新闻:NewsTag ,博客:BlogTag
     * jsonDate
     * json格式对象.其中有body title ....
     * tags
     * 这个文章所包含的tag.
     */
    @At(path = "/doc/{type}/insert", types = POST)
    public void save() {

        for (String tagStr : tags) {
            Model model = (Model) invoke_model(param("type"), "create", selectMapWithAliasName(paramAsJSON("jsonData"), "id", "object_id", "created_at", "created_at"));
            model.m("tag", Tag.create(map("name", tagStr)));
            if (!model.save()) {
                render(HTTP_400, model.validateResults);
            }
        }
        render(OK);
    }

    @Inject
    private RemoteDataService remoteDataService;

    /**
     * @return 返回查询的结果集
     * @example doc/{type}/search?tagNames=java,php
     * tags               :传递 tag的名称,多个tag name 按","分割
     * channelIds         : 分类id .比如资讯有四个分类。聚合的内容只会来自指定的分类
     * blockedTagsNames   ： 希望聚合的文章不包括的tag name。按","分割。
     * orderFields        : 排序字段。默认为"weight".多个按","分割
     * orderFieldsDescAsc : 排序顺序。默认desc.需要和order_fields对应
     * isOriginal		 : 是否原创 true or false
     * start		         :分页用。开始条数。默认0
     * size: 		     :一页显示条数。默认15
     * type               :typeName doc所属的类型.example 新闻:NewsTag ,博客:BlogTag
     * @description 可排序字段  id  create_at weight
     */
    @At(path = "/doc/{type}/search", types = GET)
    public void search() {

        Set<String> newTags = Tag.synonym(param("tags"));


        JPQL query = (JPQL) invoke_model(param("type"), "where", "tag.name in (" + join(newTags, ",", "'") + ")");

        if (!isEmpty(param("channelIds"))) {
            String channelIds = join(param("channelIds").split(","), ",", "'");
            query.where("channel_id in (" + channelIds + ")");
        }

        if (!isEmpty(param("blockedTagsNames"))) {
            String blockedTagsNames = join(param("blockedTagsNames").split(","), ",", "'");
            String abc = "select object_id from " + param("type") + " where  tag.name in (" + blockedTagsNames + ")";
            query.where("object_id not in (" + abc + ")");
        }

        long count = query.count_fetch("count(distinct object_id ) as count");

        if (!isEmpty("orderFields")) {
            query.order(order());
        }

        List<Model> models = query.offset(paramAsInt("start", 0)).limit(paramAsInt("size", 15)).fetch();

        // JSONArray data = remoteDataService.findByIds(param("type"), param("fields"), fetchObjectIds(models));

        render(map("total", count, "data", map()));
    }


    private String[] tags;

    private void checkParam() {
        tags = param("tags", " ").split(",");
        if (tags.length == 0) {
            render(HTTP_400, format(FAIL, "必须传递标签"));
        }
    }

    private Tag tag;

    private void findTag() {
        tag = Tag.where("name=:name", map("name", param("tag"))).single_fetch();
        if (tag == null) {
            render(HTTP_400, format(FAIL, "必须传递tag参数"));
        }
    }

    private String fetchObjectIds(List<Model> models) {
        List<Integer> ids = new ArrayList<Integer>(models.size());
        for (Model model : models) {
            ids.add(model.attr("object_id", Integer.class));
        }
        return join(ids, ",");
    }

    private String order() {
        String[] orderFields = param("orderFields").split(",");
        String[] orderFieldsDescAsc = param("orderFieldsDescAsc", "").split(",");
        List<String> temp = new ArrayList<String>();
        int i = 0;
        for (String str : orderFields) {
            if (orderFieldsDescAsc.length < i) {
                temp.add(str + " " + orderFieldsDescAsc[i]);
            } else {
                temp.add(str + " " + "desc");
            }

        }
        return join(temp, ",");
    }

    private Object invoke_model(String type, String method, Object... params) {
        return ReflectHelper.method(const_model_get(type), method, params);
    }

}
