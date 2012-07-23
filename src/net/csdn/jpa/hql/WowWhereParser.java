package net.csdn.jpa.hql;


import java.util.*;

import static net.csdn.common.collections.WowCollections.*;

/**
 * User: WilliamZhu
 * Date: 12-7-21
 * Time: 下午8:28
 * note:
 * 目前实现一个简单字符解析器。适当的时候将它调整为语法树。
 */
public class WowWhereParser {

    //将where子句进行切分
    public static final String HQL_SEPARATORS = " \n\r\f\t,()=<>&|+-=/*'^![]#~\\";

    private Set columns = new HashSet<String>();
    private String alias = "";
    private List<String> whereConditions = new ArrayList<String>();
    private boolean open = false;
    private boolean close = true;
    private List<String> smallExpression = new ArrayList<String>();


    public WowWhereParser(Set columns, String alias) {
        this.columns = columns;
        this.alias = alias;
    }

    public void parse(String wheres) {
        StringTokenizer tokens = new StringTokenizer(wheres, HQL_SEPARATORS, true);
        while (tokens.hasMoreElements()) {
            String token = tokens.nextToken();
            whereConditions.add(this.token(token));
        }
    }

    public String toHql() {
        return join(whereConditions);
    }
    //private String

    private String token(String token) {
        String lcToken = token.toLowerCase().trim();
        if (open && !close) {
            if (lcToken.equals("'") || lcToken.equals("\"")) {
                open = false;
                close = true;
                smallExpression.add(token);
                return join(smallExpression);
            }
            smallExpression.add(token);
            return "";
        }

        if (lcToken.trim().isEmpty()
                || lcToken.startsWith(":")
                || EXPRESSION_TERMINATORS.contains(lcToken)
                || BOOLEAN_OPERATORS.contains(lcToken)
                )
            return token;

        if ((lcToken.equals("'") || lcToken.equals("\"")) && (!open && close)) {
            open = true;
            close = false;
            smallExpression.clear();
            smallExpression.add(token);
            return "";
        }
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


    private static final Set EXPRESSION_TERMINATORS = new HashSet();   //tokens that close a sub expression
    private static final Set EXPRESSION_OPENERS = new HashSet();       //tokens that open a sub expression
    private static final Set BOOLEAN_OPERATORS = new HashSet();        //tokens that would indicate a sub expression is a boolean expression
    private static final Map NEGATIONS = new HashMap();

    static {
        EXPRESSION_TERMINATORS.add("and");
        EXPRESSION_TERMINATORS.add("or");
        EXPRESSION_TERMINATORS.add(")");
        //expressionTerminators.add(","); // deliberately excluded

        EXPRESSION_OPENERS.add("and");
        EXPRESSION_OPENERS.add("or");
        EXPRESSION_OPENERS.add("(");
        //expressionOpeners.add(","); // deliberately excluded

        BOOLEAN_OPERATORS.add("<");
        BOOLEAN_OPERATORS.add("=");
        BOOLEAN_OPERATORS.add(">");
        BOOLEAN_OPERATORS.add("#");
        BOOLEAN_OPERATORS.add("~");
        BOOLEAN_OPERATORS.add("like");
        BOOLEAN_OPERATORS.add("ilike");
        BOOLEAN_OPERATORS.add("regexp");
        BOOLEAN_OPERATORS.add("rlike");
        BOOLEAN_OPERATORS.add("is");
        BOOLEAN_OPERATORS.add("in");
        BOOLEAN_OPERATORS.add("any");
        BOOLEAN_OPERATORS.add("some");
        BOOLEAN_OPERATORS.add("all");
        BOOLEAN_OPERATORS.add("exists");
        BOOLEAN_OPERATORS.add("between");
        BOOLEAN_OPERATORS.add("<=");
        BOOLEAN_OPERATORS.add(">=");
        BOOLEAN_OPERATORS.add("=>");
        BOOLEAN_OPERATORS.add("=<");
        BOOLEAN_OPERATORS.add("!=");
        BOOLEAN_OPERATORS.add("<>");
        BOOLEAN_OPERATORS.add("!#");
        BOOLEAN_OPERATORS.add("!~");
        BOOLEAN_OPERATORS.add("!<");
        BOOLEAN_OPERATORS.add("!>");
        BOOLEAN_OPERATORS.add("is not");
        BOOLEAN_OPERATORS.add("not like");
        BOOLEAN_OPERATORS.add("not ilike");
        BOOLEAN_OPERATORS.add("not regexp");
        BOOLEAN_OPERATORS.add("not rlike");
        BOOLEAN_OPERATORS.add("not in");
        BOOLEAN_OPERATORS.add("not between");
        BOOLEAN_OPERATORS.add("not exists");

        NEGATIONS.put("and", "or");
        NEGATIONS.put("or", "and");
        NEGATIONS.put("<", ">=");
        NEGATIONS.put("=", "<>");
        NEGATIONS.put(">", "<=");
        NEGATIONS.put("#", "!#");
        NEGATIONS.put("~", "!~");
        NEGATIONS.put("like", "not like");
        NEGATIONS.put("ilike", "not ilike");
        NEGATIONS.put("regexp", "not regexp");
        NEGATIONS.put("rlike", "not rlike");
        NEGATIONS.put("is", "is not");
        NEGATIONS.put("in", "not in");
        NEGATIONS.put("exists", "not exists");
        NEGATIONS.put("between", "not between");
        NEGATIONS.put("<=", ">");
        NEGATIONS.put(">=", "<");
        NEGATIONS.put("=>", "<");
        NEGATIONS.put("=<", ">");
        NEGATIONS.put("!=", "=");
        NEGATIONS.put("<>", "=");
        NEGATIONS.put("!#", "#");
        NEGATIONS.put("!~", "~");
        NEGATIONS.put("!<", "<");
        NEGATIONS.put("!>", ">");
        NEGATIONS.put("is not", "is");
        NEGATIONS.put("not like", "like");
        NEGATIONS.put("not ilike", "ilike");
        NEGATIONS.put("not regexp", "regexp");
        NEGATIONS.put("not rlike", "rlike");
        NEGATIONS.put("not in", "in");
        NEGATIONS.put("not between", "between");
        NEGATIONS.put("not exists", "exists");

    }
}
