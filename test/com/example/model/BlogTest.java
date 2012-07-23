package com.example.model;

import net.csdn.junit.IocTest;
import net.csdn.jpa.JPA;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static net.csdn.common.collections.WowCollections.map;
import static org.junit.Assert.assertTrue;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-1
 * Time: 下午2:26
 */
public class BlogTest extends IocTest {
    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        Blog.deleteAll();
        BlogBody.deleteAll();
    }

    @Test
    public void testAssociated() throws Exception {
        Blog blog = Blog.create(map("user_name", "jjj", "blog_body.content", ""));

        assertTrue(blog.valid() == false);
        assertTrue(blog.validateResults.size() == 1);

    }

    @Test
    public void testParam() throws Exception {
        Blog blog = Blog.create(map("content", "天国", "blog_body.content", "wow"));
        assertTrue(blog
                .attr("blog_body", BlogBody.class)
                .attr("content", String.class)
                .equals("wow"));
    }

    @Test
    public void testFind()throws Exception{
    }

    @Test
    public void testSql(){
         Blog.where("id=:id", map("id", 1)).joins("join blog_comments").fetch();
    }

    @Test
    public void testBlogInfoAndBlog() throws Exception {
        Blog blog = Blog.create(map("user_name", "jjj", "blog_info.info", ""));
        blog.save();
    }

    @Test
    public void testCasa2() throws Exception {
        Blog blog = Blog.create(map("user_name", "jjj"));
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
//        Article article = blog.m("articles").add(newHashMap("content", "性能设计"));
//
//        assertTrue(article.validateResults.size() == 0);
//        assertTrue(article.attr("id", Integer.class) != null);
//        assertTrue(article.attr("content", String.class).equals("性能设计"));
//
//        Article.deleteAll();
//
//        article = blog.m("articles").add(newHashMap("content", ""));
//
//        assertTrue(article.validateResults.size() > 0);
//        assertTrue(article.attr("id", Integer.class) == null);
//
//        //oneToOne
//        blog = Blog.create(newHashMap("user_name", "jjj", "blog_info.info", "wow"));
//        blog.save();


    }

    @Test
    public void testCasa() throws Exception {
//        Blog blog = Blog.create(newHashMap("user_name", "wow"));
//        blog.save();
//        Article article = Article.create(newHashMap("content", "我是天才哇"));
//        article.attr("blog", blog);
//        article.save();
//
////        List<Blog> blogs = Blog.where("user_name='wow'").fetch();
////        blog = blogs.get(0);
////        Assert.assertTrue(blog.articles.get(0).attr("content", String.class).equals("我是天才哇"));
//
//        List<Article> articles = Article.findAll();
//        article = articles.get(0);
//        assertTrue(article.attr("blog", Blog.class).attr("user_name", String.class).equals("wow"));


    }

    @Test
    public void testLengthValidate() throws Exception {
        Blog blog = Blog.create(map("user_name", "wow"));
        assertTrue(blog.valid() == true);

        blog = Blog.create(map("user_name", "wowwowwowwowwowwowwowwowwowwowwowwowwowwowwowwowwowwowwowwow"));
        assertTrue(blog.valid() == false);
        assertTrue(blog.validateResults.get(0).getMessage().equals("user_name文字太长"));


        blog = Blog.create(map("user_name", "w"));
        assertTrue(blog.valid() == false);
        assertTrue(blog.validateResults.get(0).getMessage().equals("user_name文字太短"));
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
        Blog blog = Blog.create(map("user_name", "wow"));
        assertTrue(blog.valid() == true);

        blog.save();

        blog = Blog.create(map("user_name", "wow"));
        assertTrue(blog.valid() == false);

        assertTrue(blog.validateResults.get(0).getMessage().equals("user_name is not uniq"));


    }


    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        JPA.getJPAConfig().getJPAContext().closeTx(false);
    }
}
