package com.example.controller;

import com.example.model.Tag;
import com.example.model.TagSynonym;
import net.csdn.annotation.At;
import net.csdn.annotation.BeforeFilter;
import net.csdn.jpa.model.JPQL;
import net.csdn.jpa.model.Model;
import net.csdn.modules.http.ApplicationController;
import net.csdn.reflect.ReflectHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.*;
import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.filter.FilterHelper.BeforeFilter.only;
import static net.csdn.modules.http.RestRequest.Method.GET;
import static net.csdn.modules.http.RestRequest.Method.POST;
import static net.csdn.modules.http.support.HttpStatus.HTTP_400;


public class TagController extends ApplicationController {

    @BeforeFilter
    private final static Map $checkParam = map(only, list("save", "search"));


    /**
     * 通过插入doc 增加tag和doc的关联表
     * <p/>
     * type
     * doc所属的类型.example 新闻:NewsTag ,博客:BlogTag
     * jsonDate
     * json格式对象.其中有body title ....
     * tags
     * 这个文章说包含的tag.
     */
    @At(path = "/doc/{type}/insert", types = POST)
    public void save() {

        for (String tagStr : tags) {
            Model model = (Model) invoke_model(param("type"), "create", selectMapWithAliasName(paramAsJSON("jsonData"), "id", "object_id", "created_at", "created_at"));
            model.attr("tag", Tag.create(map("name", tagStr)));
            if (!model.save()) {
                render(HTTP_400, model.validateResults);
            }
        }
        render(OK);
    }

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

        String newTags = Tag.synonym(param("tags"));

        JPQL jpql = (JPQL) invoke_model(param("type"), "where", "tag.name in (" + newTags + ")");

        if (!isEmpty(param("channelIds"))) {
            String channelIds = join(param("channelIds").split(","), ",", "'");
            jpql.where("channel_id in (" + channelIds + ")");
        }

        if (!isEmpty(param("blockedTagsNames"))) {
            String blockedTagsNames = join(param("blockedTagsNames").split(","), ",", "'");
            jpql.where("tag.name in (" + blockedTagsNames + ")");
        }

        long count = jpql.count_fetch();

        if (!isEmpty("orderFields")) {
            jpql.order(order());
        }

        List<Model> models = jpql.offset(paramAsInt("start", 0)).limit(paramAsInt("size", 15)).fetch();
        render(map("total", count, "data", models));
    }

    private String[] tags;

    private void checkParam() {
        tags = param("tags", " ").split(",");
        if (tags.length == 0) {
            render(HTTP_400, format(FAIL, "必须传递标签"));
        }
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
