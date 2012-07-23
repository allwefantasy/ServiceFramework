package net.csdn.jpa.hql;

import java.util.*;

import static net.csdn.common.collections.WowCollections.join;
import static net.csdn.common.collections.WowCollections.newHashSet;

/**
 * User: WilliamZhu
 * Date: 12-7-23
 * Time: 下午1:25
 */
public class WowJoinParser {

    public static final String HQL_JOIN_SEPARATORS = WowWhereParser.HQL_SEPARATORS;
    private Set<String> keywords = newHashSet("join", "left join", "right join", "inner join", "outer join");

    private Set columns = new HashSet<String>();
    private String alias = "";
    private List<String> joinClauses = new ArrayList<String>();

    private boolean open = false;
    private boolean close = true;

    public WowJoinParser(Set columns, String alias) {
        this.columns = columns;
        this.alias = alias;
    }

    public void parse(String joins) {

        StringTokenizer tokens = new StringTokenizer(joins, HQL_JOIN_SEPARATORS, true);
        while (tokens.hasMoreElements()) {
            joinClauses.add(this.token(tokens.nextToken()));
        }


    }

    public String toHql() {
        return join(joinClauses);
    }

    private String token(String token) {
        String lcToken = token.toLowerCase().trim();
        if (lcToken.equals("left")
                || lcToken.equals("right")
                || lcToken.equals("inner")
                || lcToken.equals("outer")
                || lcToken.equals("inner")
                || lcToken.equals("join")) {
            return token;
        }
        String prefixName = WowWhereParser.root(token);
        if (columns.contains(prefixName)) {
            if (prefixName.equals(alias)) {
                return token;
            } else {
                return alias + "." + token;
            }
        }
        return token;
    }

}
