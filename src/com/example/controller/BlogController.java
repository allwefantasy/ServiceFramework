package com.example.controller;

import com.example.model.Article;
import com.example.model.Blog;
import com.example.service.blog.BlogService;
import com.google.inject.Inject;
import net.csdn.annotation.At;
import net.csdn.annotation.BeforeFilter;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.exception.RecordNotFoundException;
import net.csdn.filter.FilterHelper;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.ViewType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.newArrayList;
import static net.csdn.common.collections.WowCollections.newHashMap;
import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.filter.FilterHelper.BeforeFilter.except;
import static net.csdn.filter.FilterHelper.BeforeFilter.only;
import static net.csdn.modules.http.RestRequest.Method.GET;
import static net.csdn.modules.http.RestRequest.Method.POST;
import static net.csdn.modules.http.support.HttpStatus.HttpStatusBadRequest;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-1
 * Time: 下午8:16
 */
public class BlogController extends ApplicationController {

    @Inject
    BlogService blogService;

    @BeforeFilter
    private final static Map $filter1 = newHashMap(only, newArrayList("createBlog", "find"));
    @BeforeFilter
    private final static Map $filter2 = newHashMap(except, newArrayList("createBlog", "find"));

    @BeforeFilter
    private final static Map $filter3 = newHashMap();


    //需要Service的Action
    @At(path = {"/hello"}, types = {GET})
    public void hello() {
        render(format(OK, blogService.createBlog()), ViewType.xml);
    }

    @At(path = {"/blog/articles/create"}, types = {POST})
    public void createArticle() {
        Blog blog = Blog.findById(param("id"));
        Article article = blog.m("articles").add(newHashMap("content", param("content")));
        if (article.validateResults.size() != 0) {
            render(HttpStatusBadRequest, article.validateResults);
        }
        render(format(OK, "文章添加成功"));
    }


    @At(path = {"/blog"}, types = {POST})
    public void createBlog() {
        Blog blog = Blog.create(paramAsJSON());
        if (!blog.valid()) {
            render(HttpStatusBadRequest, blog.validateResults);
        }
        render(format(OK, "博客创建成功"));
    }

    @At(path = {"/blog/{id}"}, types = {GET})
    public void find() {
        Blog blog = Blog.findById(paramAsInt("id"));
        if (blog == null) throw new RecordNotFoundException("没有找到ID为[" + param("id") + "]的博客");
        render(blog, ViewType.xml);
    }


    @At(path = {"/blog/articles"}, types = {GET})
    public void articles() {
        Blog blog = Blog.findById(paramAsInt("id"));
        List<Article> articles = Article.where("blog=:blog", newHashMap("blog", blog))
                .offset(paramAsInt("from", 1))
                .limit(paramAsInt("size", 15))
                .order("id desc")
                .fetch();
        render(articles);
    }

    private void filter1() {
        logger.info("filter1");
        render("createBlog,find will not execute");
    }

    private void filter2() {
        logger.info("filter2");
    }

    private void filter3() {
        logger.info("filter3");

    }

    private CSLogger logger = Loggers.getLogger(getClass());
}
