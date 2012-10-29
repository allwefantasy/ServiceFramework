package net.csdn.jpa.hql;

import java.util.*;

import static net.csdn.common.collections.WowCollections.join;

/**
 * User: WilliamZhu
 * Date: 12-7-28
 * Time: 下午11:54
 */
public class WowCommonParser {
    public static final String HQL_JOIN_SEPARATORS = WowWhereParser.HQL_SEPARATORS;
    private Set columns = new HashSet<String>();
    private String alias = "";
    private List<String> orderList = new ArrayList<String>();


    public WowCommonParser(Set columns, String alias) {
        this.columns = columns;
        this.alias = alias;
    }

    public void parse(String common) {
        StringTokenizer tokens = new StringTokenizer(common, HQL_JOIN_SEPARATORS, true);
        while (tokens.hasMoreElements()) {
            String token = tokens.nextToken();
            orderList.add(this.token(token));
        }
    }

    public String toHql() {
        return join(orderList);
    }
    //private String

    private String token(String token) {
        String prefixName = root(token);
        if (columns.contains(prefixName)) {
            if (prefixName.equals(alias)) {
                return token;
            } else {
                return alias + "." + token;
            }
        }
        return token;
    }

    public static String root(String qualifiedName) {
        int loc = qualifiedName.indexOf(".");
        return (loc < 0) ? qualifiedName : qualifiedName.substring(0, loc);
    }
}
