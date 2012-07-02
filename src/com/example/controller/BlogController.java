package com.example.controller;

import com.example.model.Blog;
import com.example.service.hello.HelloService;
import com.google.inject.Inject;
import net.csdn.annotation.At;
import net.csdn.exception.RecordNotFoundException;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.ViewType;

import java.util.List;

import static net.csdn.common.collections.WowCollections.newHashMap;
import static net.csdn.common.logging.support.MessageFormat.format;
import static net.csdn.modules.http.RestRequest.Method.GET;
import static net.csdn.modules.http.RestRequest.Method.POST;

/**
 * User: WilliamZhu
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

    //简单的web+数据库 请求
    //处理json请求
    @At(path = {"/blog"}, types = {POST})
    public void saveJson() {

        Blog blog = Blog.create(paramAsJSON());

        blog.save();

        render(format(OK, "博客创建成功"));
    }

    //处理普通表单请求请求
    @At(path = {"/blog/{id}"}, types = {POST})
    public void saveForm() {

        Blog blog = Blog.create(params());

        blog.save();

        render(format(OK, "博客创建成功"));
    }

    @At(path = {"/blog/{id}"}, types = {GET})
    public void find() {
        Blog blog = Blog.findById(paramAsInt("id"));
        if (blog == null) throw new RecordNotFoundException("没有找到ID为[" + param("id") + "]的博客");
        render(blog, ViewType.xml);
    }

    @At(path = {"/blog"}, types = {GET})
    public void index() {
        List<Blog> blogs = Blog.where("id>:id", newHashMap("id", 1)).offset(1).limit(15).order("id desc").fetch();
        render(blogs);
    }

}
