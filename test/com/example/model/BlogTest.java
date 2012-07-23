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
    public void testSql() {
        Blog.where("id=:id", map("id", 1)).joins("join blog_comments").fetch();
    }


    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        JPA.getJPAConfig().getJPAContext().closeTx(false);
    }
}
