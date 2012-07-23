package net.csdn.jpa.hql;

import org.junit.Assert;
import org.junit.Test;

import static net.csdn.common.collections.WowCollections.newHashSet;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 上午11:40
 */
public class WowWhereParserTest {
    @Test
    public void testParse() throws Exception {
        String query = ("blog.id > 1 and(google='cn') or (a.java=:cbj AND jk>=8) and jjjj between 0 and 100");
        WowWhereParser wowWhereParser = new WowWhereParser(newHashSet("id", "google", "java", "jjjj"), "blog");
        wowWhereParser.parse(query);
        String hql = wowWhereParser.toHql();
        System.out.println(hql);
        Assert.assertTrue(hql.equals("blog.id > 1 and(blog.google='cn') or (blog.a.java=:c AND blog.jk>=8) and blog.jjjj between 0 and 100"));
    }
}
