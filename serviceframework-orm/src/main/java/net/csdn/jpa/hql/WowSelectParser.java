package net.csdn.jpa.hql;

import java.util.*;

import static net.csdn.common.collections.WowCollections.join;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午3:26
 */
public class WowSelectParser {
    public static final String HQL_SELECT_SEPARATORS = WowWhereParser.HQL_SEPARATORS;
    private Set columns = new HashSet<String>();
    private String alias = "";
    private List<String> selectConditions = new ArrayList<String>();


    public WowSelectParser(Set columns, String alias) {
        this.columns = columns;
        this.alias = alias;
    }

    public void parse(String wheres) {
        StringTokenizer tokens = new StringTokenizer(wheres, HQL_SELECT_SEPARATORS, true);
        while (tokens.hasMoreElements()) {
            String token = tokens.nextToken();
            selectConditions.add(this.token(token));
        }
    }

    public String toHql() {
        return "select " + join(selectConditions);
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
