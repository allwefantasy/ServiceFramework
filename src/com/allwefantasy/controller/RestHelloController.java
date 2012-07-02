package com.allwefantasy.controller;

import com.allwefantasy.model.Blog;
import com.allwefantasy.service.hello.HelloService;
import com.google.inject.Inject;
import net.csdn.annotation.At;
import net.csdn.exception.RecordNotFoundException;
import net.csdn.modules.http.BaseRestHandler;

import java.util.List;

import static net.csdn.common.collections.WowCollections.newHashMap;
import static net.csdn.modules.http.RestRequest.Method.GET;
import static net.csdn.modules.http.RestRequest.Method.POST;

/**
 * User: WilliamZhu
 * Date: 12-7-1
 * Time: 下午8:16
 */
public class RestHelloController extends BaseRestHandler {

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

        Blog blog = Blog.create(contentAsJSON());

        blog.save();

        render(OK, "博客创建成功");
    }

    //处理普通表单请求请求
    @At(path = {"/blog/{id}"}, types = {POST})
    public void saveForm() {

        Blog blog = Blog.create(params());

        blog.save();

        render(OK, "博客创建成功");
    }

    @At(path = {"/blog/{id}"}, types = {GET})
    public void find() {
        Blog blog = Blog.findById(param("id"));
        if (blog == null) throw new RecordNotFoundException("没有找到ID为[" + param("id") + "]的博客");
        render(blog);
    }

    @At(path = {"/blog"}, types = {GET})
    public void index() {
        List<Blog> blogs = Blog.where("id>:id", newHashMap("id", 1)).offset(1).limit(15).order("id desc").fetch();
        render(blogs);
    }

}
