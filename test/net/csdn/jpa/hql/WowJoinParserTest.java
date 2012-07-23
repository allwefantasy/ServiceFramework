package net.csdn.jpa.hql;

import org.junit.Test;

import static net.csdn.common.collections.WowCollections.newHashSet;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午1:38
 */
public class WowJoinParserTest {
    @Test
    public void testParse() throws Exception {
        String join = "join comments left  join googles out outer yes";
        WowJoinParser wowJoinParser = new WowJoinParser(newHashSet("comments", "googles", "yes"), "blog");
        wowJoinParser.parse(join);
        System.out.println(wowJoinParser.toHql());
    }
}
