package com.example.controller.tag;

import com.example.controller.ApplicationController;
import com.example.model.BlogTag;
import com.example.model.Tag;
import com.example.service.tag.RemoteDataService;
import com.google.inject.Inject;
import net.csdn.annotation.filter.AroundFilter;
import net.csdn.annotation.filter.BeforeFilter;
import net.csdn.annotation.rest.At;
import net.csdn.jpa.model.JPQL;
import net.csdn.jpa.model.Model;
import net.csdn.modules.http.RestController;
import net.csdn.reflect.ReflectHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.csdn.filter.FilterHelper.BeforeFilter.only;
import static net.csdn.modules.http.RestRequest.Method.GET;
import static net.csdn.modules.http.RestRequest.Method.PUT;
import static net.csdn.modules.http.support.HttpStatus.HTTP_400;


public class TagController extends ApplicationController {

    @BeforeFilter
    private final static Map $check_params = map(only, list("save", "search"));
    @BeforeFilter
    private final static Map $find_tag = map(only, list("create_blog_tag"));


    @AroundFilter
    private final static Map $print_action_execute_time2 = map(only, list("search"));

    private void print_action_execute_time2(RestController.WowAroundFilter wowAroundFilter) {
        long time1 = System.currentTimeMillis();
        wowAroundFilter.invoke();
        logger.info("标签聚合消耗时间:[" + (System.currentTimeMillis() - time1) + "]");

    }

    @At(path = "/withoutMysql", types = GET)
    public void withoutMysql() {
        render(ok());
    }

    @At(path = "/blog_tags", types = PUT)
    public void save() {

        for (String tagStr : tags) {
            Tag tag = Tag.where("name=:name", map("name", tagStr)).single_fetch();
            if (tag == null) {
                tag = Tag.create(map("name", tagStr));
                tag.save();
            }
            tag.associate("blog_tags").add(BlogTag.create(map("object_id", paramAsInt("object_id"))));
        }
        render(ok());
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
     * @description 可排序字段  id  create_at weight
     */
    @At(path = "/blog_tags/search", types = GET)
    public void search() {

        Set<String> newTags = Tag.synonym(param("tags"));

        JPQL query = BlogTag.where("tag.name in (" + join(newTags, ",", "'") + ")");

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

        if (!isEmpty(param("orderFields"))) {
            query.order(order());
        }

        List<Model> models = query.offset(paramAsInt("start", 0)).limit(paramAsInt("size", 15)).fetch();

        //设置json输出
        config.setExcludes(new String[]{"blog_tags"});
        config.setPretty(true);
        // JSONArray data = remoteDataService.findByIds(param("type"), param("fields"), fetch_object_ids(models));
        render(map("total", count, "data", models));
    }


    private String[] tags;

    private void check_params() {
        tags = param("tags", " ").split(",");
        if (tags.length == 0) {
            render(HTTP_400, fail("必须传递标签"));
        }
    }

    private Tag tag;

    private void find_tag() {
        tag = Tag.where("name=:name", map("name", param("tag"))).single_fetch();
        if (tag == null) {
            render(HTTP_400, fail("必须传递tag参数"));
        }
    }

    private String fetch_object_ids(List<Model> models) {
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

    @Inject
    private RemoteDataService remoteDataService;

}
