package com.example.controller;

import com.example.model.Article;
import com.example.model.Blog;
import net.csdn.jpa.JPA;
import net.csdn.junit.IocTest;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.RestResponse;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static net.csdn.common.collections.WowCollections.newHashMap;

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
        Blog.deleteAll();
        Article.deleteAll();
    }


    @Test
    public void testArticles() throws Exception {

        //数据准备
        final Blog blog = Blog.create(newHashMap("user_name", "jack"));
        blog.save();
        //准备一百篇博文
        for (int i = 0; i < 100; i++) {
            blog.m("articles").add(newHashMap("content", "天国" + i));
        }

        //为controller填充请求参数
        BlogController blogController = new BlogController();
        blogController.mockRequest(newHashMap("id", blog.attr("id", Integer.class).toString()), RestRequest.Method.GET, null);

        blogController.m("articles");

        RestResponse restResponse = blogController.mockResponse();
        Assert.assertTrue(restResponse.content() != null);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        JPA.getJPAConfig().getJPAContext().closeTx(false);
    }
}
