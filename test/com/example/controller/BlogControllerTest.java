package com.example.controller;

import com.example.model.Blog;
import com.example.model.BlogBody;
import com.example.model.BlogComment;
import net.csdn.jpa.JPA;
import net.csdn.junit.IocTest;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.RestResponse;
import net.sf.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.List;

import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-7-5
 * Time: 下午5:00
 */
public class BlogControllerTest extends IocTest {
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        //数据准备
        Blog blog = Blog.create(map("title", "jack", "blog_body.content", "天才呀", "created_at", new Date()));
        blog.save();
        //准备一百篇博文
        for (int i = 0; i < 3; i++) {
            BlogComment blogComment = blog.m("blog_comments").add(map("content", "你这文章真无聊" + i));
            blogComment.save();
            Assert.assertTrue(blogComment.valid());
        }
        dbCommit();
    }


    @Test
    public void TestCreateComment() throws Exception {
        List<Blog> blogs = Blog.where("title=:title", map("title", "jack")).fetch();
        Blog blog = blogs.get(0);
        BlogController blogController = injector.getInstance(BlogController.class);

        blogController.mockRequest(map(
                "id", blog.attr("id", Integer.class).toString(),
                "content", "这是为什么呢，顶顶 哇哈哈"
        ), RestRequest.Method.POST, null);

        blogController.m("findBlog");
        blogController.m("createComment");

        RestResponse restResponse = blogController.mockResponse();
        JSONObject renderResult = JSONObject.fromObject((String) restResponse.originContent());
        Assert.assertTrue(renderResult.getString("message").equals("文章添加成功"));
    }


    @Test
    public void testBlogs() throws Exception {
        BlogController blogController = injector.getInstance(BlogController.class);
        blogController.mockRequest(map(), RestRequest.Method.GET, null);

        blogController.m("blogs");

        RestResponse restResponse = blogController.mockResponse();

        List<Blog> blogs = (List<Blog>) restResponse.originContent();
        for (Blog blog : blogs) {
            Assert.assertTrue(blog.attr("status", Integer.class) == Blog.Status.active.value);
        }
    }

    @Test
    public void testArticles() throws Exception {

        List<Blog> blogs = Blog.where("title=:title", map("title", "jack")).fetch();
        Blog blog = blogs.get(0);
        BlogController blogController = injector.getInstance(BlogController.class);
        //为controller填充请求参数
        blogController = new BlogController();
        blogController.mockRequest(map("id", blog.attr("id", Integer.class).toString()), RestRequest.Method.GET, null);

        blogController.m("findBlog");
        blogController.m("comments");

        RestResponse restResponse = blogController.mockResponse();
        Assert.assertTrue(restResponse.content() != null);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        BlogComment.deleteAll();
        BlogBody.deleteAll();
        Blog.deleteAll();
        dbCommit();
    }
}
