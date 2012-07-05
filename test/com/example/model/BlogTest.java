package com.example.model;

import net.csdn.BaseServiceWithIocTest;
import net.csdn.jpa.JPA;
import net.csdn.validate.ValidateResult;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static net.csdn.common.collections.WowCollections.newHashMap;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-1
 * Time: 下午2:26
 */
public class BlogTest extends BaseServiceWithIocTest {
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        Blog.deleteAll();
        Article.deleteAll();
        BlogInfo.deleteAll();
    }

    @Test
    public void testAssociated() throws Exception {
        Blog blog = Blog.create(newHashMap("user_name", "jjj", "blog_info.info", ""));

        Assert.assertTrue(blog.valid() == false);
        Assert.assertTrue(blog.validateResults.size() == 1);

    }

    @Test
    public void testParam() throws Exception {
        Article article = Article.create(newHashMap("content", "天国", "blog.user_name", "wow"));
        Assert.assertTrue(article
                .attr("blog", Blog.class)
                .attr("user_name", String.class)
                .equals("wow"));
    }


    @Test
    public void testBlogInfoAndBlog() throws Exception {
        Blog blog = Blog.create(newHashMap("user_name", "jjj", "blog_info.info", ""));
        blog.save();
    }

    @Test
    public void testCasa2() throws Exception {
        Blog blog = Blog.create(newHashMap("user_name", "jjj"));
        blog.save();

        /*
        blog.m("articles") 相当于调用:
            public Article articles() {
                Article article = new Article();
                article.attr("blog", this);
                articles.add(article);
                return article;
            }
         */
        //manyToOne
        Article article = blog.m("articles").add(newHashMap("content", "性能设计"));

        Assert.assertTrue(article.validateResults.size() == 0);
        Assert.assertTrue(article.attr("id", Integer.class) != null);
        Assert.assertTrue(article.attr("content", String.class).equals("性能设计"));

        Article.deleteAll();

        article = blog.m("articles").add(newHashMap("content", ""));

        Assert.assertTrue(article.validateResults.size() > 0);
        Assert.assertTrue(article.attr("id", Integer.class) == null);

        //oneToOne
        blog = Blog.create(newHashMap("user_name", "jjj", "blog_info.info", "wow"));
        blog.save();

        Blog.deleteAll();
        Article.deleteAll();
        BlogInfo.deleteAll();


    }

    @Test
    public void testCasa() throws Exception {
        Blog blog = Blog.create(newHashMap("user_name", "wow"));
        blog.save();
        Article article = Article.create(newHashMap("content", "我是天才哇"));
        article.attr("blog", blog);
        article.save();

//        List<Blog> blogs = Blog.where("user_name='wow'").fetch();
//        blog = blogs.get(0);
//        Assert.assertTrue(blog.articles.get(0).attr("content", String.class).equals("我是天才哇"));

        List<Article> articles = Article.findAll();
        article = articles.get(0);
        Assert.assertTrue(article.attr("blog", Blog.class).attr("user_name", String.class).equals("wow"));


        Blog.deleteAll();
        Article.deleteAll();

    }

    @Test
    public void testLengthValidate() throws Exception {
        Blog blog = Blog.create(newHashMap("user_name", "wow"));
        Assert.assertTrue(blog.valid() == true);

        blog = Blog.create(newHashMap("user_name", "wowwowwowwowwowwowwowwowwowwowwowwowwowwowwowwowwowwowwowwow"));
        Assert.assertTrue(blog.valid() == false);
        Assert.assertTrue(blog.validateResults.get(0).getMessage().equals("user_name文字太长"));


        blog = Blog.create(newHashMap("user_name", "w"));
        Assert.assertTrue(blog.valid() == false);
        Assert.assertTrue(blog.validateResults.get(0).getMessage().equals("user_name文字太短"));
    }

    @Test
    public void testPresenceValidate() throws Exception {
//        Article article = Article.create(newHashMap("content", ""));
//        Assert.assertTrue(article.valid() == false);
//        List<ValidateResult> validateResults = article.validateResults;
//        Assert.assertTrue(validateResults.size() == 1);
//        Assert.assertTrue(validateResults.get(0).getMessage().equals("content不能为空"));
//
//        article = Article.create(newHashMap("content", "----"));
//        Assert.assertTrue(article.valid() == true);

    }


    @Test
    public void testUniquenessValidate() throws Exception {
        Blog blog = Blog.create(newHashMap("user_name", "wow"));
        Assert.assertTrue(blog.valid() == true);

        blog.save();

        blog = Blog.create(newHashMap("user_name", "wow"));
        Assert.assertTrue(blog.valid() == false);

        Assert.assertTrue(blog.validateResults.get(0).getMessage().equals("user_name is not uniq"));

        Blog.deleteAll();

    }


    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        JPA.getJPAConfig().getJPAContext().closeTx(false);
    }
}
