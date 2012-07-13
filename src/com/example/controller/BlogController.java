package com.example.controller;

import com.example.model.Blog;
import com.example.model.BlogComment;
import com.example.service.blog.BlogService;
import com.google.inject.Inject;
import net.csdn.annotation.At;
import net.csdn.annotation.BeforeFilter;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.ViewType;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.list;
import static net.csdn.common.collections.WowCollections.map;
import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.filter.FilterHelper.BeforeFilter.except;
import static net.csdn.modules.http.RestRequest.Method.GET;
import static net.csdn.modules.http.RestRequest.Method.POST;
import static net.csdn.modules.http.support.HttpStatus.HttpStatusBadRequest;
import static net.csdn.modules.http.support.HttpStatus.HttpStatusNotFound;

/**
 * 说明:
 * 1 你可以使用IOC容器提供的一切功能
 * 2 controller是多线程安全的
 */
public class BlogController extends ApplicationController {

    @Inject
    BlogService blogService;

    @BeforeFilter
    private final static Map $findBlog = map(except, list("hello", "createBlog", "blogs"));


    private Blog blog;

    //需要Service的Action
    @At(path = {"/hello"}, types = {GET})
    public void hello() {
        render(format(OK, blogService.createBlog()), ViewType.xml);
    }


    @At(path = {"/blog"}, types = {POST})
    public void createBlog() {
        Blog blog = Blog.create(paramAsJSON());
        if (!blog.valid()) {
            render(HttpStatusBadRequest, blog.validateResults);
        }
        render(format(OK, "博客创建成功"));
    }


    @At(path = {"/blog/comments"}, types = {POST})
    public void createComment() {
        BlogComment blogComment = blog.m("blog_comments").add(map("content", param("content")));
        if (!blogComment.valid()) {
            render(HttpStatusBadRequest, blogComment.validateResults);
        }
        render(format(OK, "文章添加成功"));
    }


    @At(path = {"/blog/{id}"}, types = {GET})
    public void find() {
        render(blog, ViewType.xml);
    }

    @At(path = {"/blogs"}, types = {GET})
    public void blogs() {
        List<Blog> blogs = Blog.activeBlogs.offset(0).limit(15).fetch();
        render(blogs);
    }


    @At(path = {"/blog/comments"}, types = {GET})
    public void comments() {
        Blog blog = Blog.findById(paramAsInt("id"));
        List<BlogComment> blogComments = BlogComment.where("blog=:blog", map("blog", blog))
                .offset(paramAsInt("from", 0))
                .limit(paramAsInt("size", 15))
                .order("id desc")
                .fetch();
        render(blogComments);
    }

    @At(path = {"/blog/comments2"}, types = {GET})
    public void comments2() {
        List<Blog> blogs = Blog.where("id=:id", map("id", paramAsInt("id"))).joins(" blog.blog_comments").fetch();
        if (blogs.size() == 0) {
            render(HttpStatusNotFound, format(FAIL, format("没有找到ID为[{}]的博文", param("id"))));
        }
        render(blogs.get(0).attr("blog_comments", List.class));
    }

    private void findBlog() {
        blog = Blog.findById(paramAsInt("id"));
        if (blog == null) render(HttpStatusBadRequest, format(FAIL, "必须输入博客ID"));
    }


    private CSLogger logger = Loggers.getLogger(getClass());
}
