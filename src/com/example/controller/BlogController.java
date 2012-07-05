package com.example.controller;

import com.example.model.Article;
import com.example.model.Blog;
import com.example.service.hello.HelloService;
import com.google.inject.Inject;
import net.csdn.annotation.At;
import net.csdn.exception.RecordNotFoundException;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.ViewType;
import net.csdn.modules.http.support.HttpStatus;
import net.csdn.validate.ValidateResult;

import java.util.List;

import static net.csdn.common.collections.WowCollections.newHashMap;
import static net.csdn.common.logging.support.MessageFormat.format;
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
    HelloService helloService;


    //需要Service的Action
    @At(path = {"/hello"}, types = {GET})
    public void hello() {
        render(helloService.sayHello());
    }

    @At(path = {"/blog/articles/create"}, types = {POST})
    public void createArticle() {
        Blog blog = Blog.findById(param("id"));
        Article article = blog.m("articles").add(newHashMap("content", param("content")));
        if (article.validateResults.size() != 0) {
            render(HttpStatusBadRequest, article.validateResults);
        }
        render(article);
    }


    @At(path = {"/blog"}, types = {POST})
    public void createBlog() {

        Blog blog = Blog.create(paramAsJSON());
        if (blog.valid()) {
            blog.save();
        } else {
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


}
