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

    }
}
