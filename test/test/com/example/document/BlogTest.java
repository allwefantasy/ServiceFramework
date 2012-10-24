package test.com.example.document;

import com.example.document.Article;
import com.example.document.Blog;
import junit.framework.Assert;
import net.csdn.junit.IocTest;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.list;
import static net.csdn.common.collections.WowCollections.map;

/**
 * User: WilliamZhu
 * Date: 12-10-24
 * Time: 上午11:44
 */
public class BlogTest extends IocTest {


    public void embeddedDocumentDeleteTest() {
        Map blogMap = map(
                "_id", 100,
                "userName", "jack",
                "blogTitle", "this is a test blog",
                "articles", list(
                map(

                        "title", "article",
                        "body", "article body"
                ),
                map(

                        "title", "article1",
                        "body", "article body1"
                )
        )
        );

        Blog blog = Blog.create(blogMap);
        blog.save();
        blog = Blog.findById(100);
        List<Article> articles = blog.articles().find();
        Assert.assertTrue(articles.size() == 2);
        Article article = articles.get(0);
        article.remove();

        blog = Blog.findById(100);
        articles = blog.articles().find();
        Assert.assertTrue(articles.size() == 1);
    }


    @Test
    public void embeddedDocumentTest() {
        Map blogMap = map(
                "_id", 100,
                "userName", "jack",
                "blogTitle", "this is a test blog",
                "articles", list(
                map(
                        "_id", 101,
                        "title", "article",
                        "body", "article body"
                ),
                map(
                        "_id", 102,
                        "title", "article1",
                        "body", "article body1"
                )
        )
        );

        Blog blog = Blog.create(blogMap);
        blog.save();

        List<Article> articleList = blog.articles().find();
        Assert.assertTrue(articleList.size() == 2);

        articleList = blog.articles().find(101);
        Assert.assertTrue(articleList.size() == 1);

        Article article = articleList.get(0);
        Assert.assertTrue(blog.id().equals(article.blog().findOne().id()));

        //test delete
        articleList = blog.articles().find();

        Assert.assertTrue(articleList.size() == 2);
        article = articleList.get(0);
        article.remove();

        articleList = blog.articles().find();
        Assert.assertTrue(articleList.size() == 1);


    }
}
