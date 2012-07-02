package net.csdn.modules.parser.query;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.context.SearchContext;
import net.csdn.modules.analyzer.AnalyzerService;
import net.csdn.modules.analyzer.mmseg4j.analysis.MMSegAnalyzer;
import net.csdn.modules.http.JSONObjectUtils;
import net.csdn.modules.gateway.GatewayData;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.lucene.index.Engine;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.util.NumericUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static net.csdn.modules.http.JSONObjectUtils.*;


/**
 * User: william
 * Date: 11-9-7
 * Time: 上午11:19
 */
public class QueryParseElement implements SearchParseElement {

    private static CSLogger logger = Loggers.getLogger(QueryParseElement.class);

    @Inject
    private static GatewayData gatewayData;
    @Inject
    private static AnalyzerService analyzerService;

    public enum QUERY_BOOLEAN {
        term, text, range, bool, topEnable, wildcard
    }

    /* "query" : {
    *        "term" : { "user" : "kimchy" },
    *        "text" : { "title" : "hey kick my ass wow","body":"your jacket is  cool" },
    *        "bool" :{},
    *        "range":{},
    *        "topEnable":false,
    *        "operator":"and"
    *    }
    */
    @Override
    public void parse(JSONObject obj, SearchContext context) {
        List<QueryParse> parserList = Lists.newArrayList(new BoolParse(), new AndParse(), new OrParse(),
                new NotParse(), new TermParse(), new RangeParse(),
                new TextParse(), new MatchAllParse(), new WildcardParse());


        obj = getJSONObject(obj, SearchContext.QUERY_FIRST_KEY_WORD.QUERY);
        if (obj == null) return;
        //解析之后的最终query
        BooleanQuery query = new BooleanQuery();

        QueryParser.Operator operator = QueryParser.Operator.valueOf(getString(obj, "operator", "and").toUpperCase());

        if (obj.containsKey(QUERY_BOOLEAN.topEnable.name())) {
            context.topEnable(getBoolean(obj, QUERY_BOOLEAN.topEnable.name()));
        } else {
            context.topEnable(false);
        }
        //解析之后的最终query
        for (QueryParse filterParser : parserList) {
            Query tempQuery = filterParser.parse(obj).toQuery();
            if (tempQuery != null) {
                query.add(new BooleanClause(tempQuery, BooleanClause.Occur.MUST));
            }
        }
        if (query.clauses().size() == 1) {
            context.query(query.clauses().get(0).getQuery());
            return;
        }
        context.query(query);

    }


    interface QueryParse {
        public QueryParse parse(JSONObject baseObject);

        public Query toQuery();
    }

    static class MatchAllParse implements QueryParse {
        private MatchAllDocsQuery matchAllDocsQuery = null;

        public QueryParse parse(JSONObject baseObject) {
            boolean matchAll = JSONObjectUtils.getBoolean(baseObject, "match_all", false);
            if (!matchAll) {
                return this;
            }
            matchAllDocsQuery = new MatchAllDocsQuery();
            return this;
        }

        public Query toQuery() {
            return optimize(matchAllDocsQuery);
        }

    }

    static class TermParse implements QueryParse {
        private BooleanQuery termBooleanQuery = null;

        public QueryParse parse(JSONObject baseObject) {
            termBooleanQuery = null;
            JSONObject termJSON = getJSONObject(baseObject, "term", getJSONObject(baseObject, "field"));

            if (termJSON == null || termJSON.isEmpty()) {
                return this;
            }
            termBooleanQuery = new BooleanQuery();

            String operator = JSONObjectUtils.getString(termJSON, "operator", "must").toUpperCase();
            termJSON.remove("operator");

            for (Iterator i = termJSON.keys(); i.hasNext(); ) {
                String key = (String) i.next();
                String value = termJSON.getString(key);
                termBooleanQuery.add(new BooleanClause(new TermQuery(new Term(key, termConvertor(key, value))), BooleanClause.Occur.valueOf(operator)));
            }
            return this;
        }

        public Query toQuery() {
            return optimize(termBooleanQuery);
        }

    }


    public static String termConvertor(String filedName, String value) {
        SearchContext searchContext = SearchContext.current();
        Engine.Mapper mapper = gatewayData.mapper(searchContext.name(), searchContext.types()[0]);
        Engine.MapperType mapperType = mapper._properties(filedName);
        if ("integer".equalsIgnoreCase(mapperType.type())) {
            return NumericUtils.intToPrefixCoded(Integer.parseInt(value));
        } else if ("float".equalsIgnoreCase(mapperType.type())) {
            return NumericUtils.floatToPrefixCoded(Float.parseFloat(value));
        } else if ("double".equalsIgnoreCase(mapperType.type())) {
            return NumericUtils.doubleToPrefixCoded(Double.parseDouble(value));
        } else if ("long".equalsIgnoreCase(mapperType.type())) {
            return NumericUtils.longToPrefixCoded(Long.parseLong(value));
        } else {
            return value;
        }
    }

    static class RangeParse implements QueryParse {
        private Query query = null;

        @Override
        public QueryParse parse(JSONObject baseObject) {
            query = null;
            JSONObject termKeyValues = baseObject.getJSONObject("range");
            if (termKeyValues == null || termKeyValues.isEmpty()) return this;
            String rangeField = (String) termKeyValues.keys().next();
            JSONObject obj = termKeyValues.getJSONObject(rangeField);
            SearchContext searchContext = SearchContext.current();
            Engine.Mapper mapper = gatewayData.mapper(searchContext.name(), searchContext.types()[0]);
            Engine.MapperType mapperType = mapper._properties(rangeField);
            if (mapperType.type().equals("integer"))
                query = NumericRangeQuery.newIntRange(rangeField, getInt(obj, "from"), getInt(obj, "to"), getBoolean(obj, "include_lower"), getBoolean(obj, "include_upper"));
            else if (mapperType.type().equals("float"))
                query = NumericRangeQuery.newFloatRange(rangeField, getFloat(obj, "from"), getFloat(obj, "to"), getBoolean(obj, "include_lower"), getBoolean(obj, "include_upper"));
            else if (mapperType.type().equals("double"))
                query = NumericRangeQuery.newDoubleRange(rangeField, getDouble(obj, "from"), getDouble(obj, "to"), getBoolean(obj, "include_lower"), getBoolean(obj, "include_upper"));
            else if (mapperType.type().equals("long"))
                query = NumericRangeQuery.newLongRange(rangeField, getLong(obj, "from"), getLong(obj, "to"), getBoolean(obj, "include_lower"), getBoolean(obj, "include_upper"));
            else {
                query = new TermRangeQuery(rangeField, getString(obj, "from"), getString(obj, "to"), getBoolean(obj, "include_lower"), getBoolean(obj, "include_upper"));
            }
            return this;
        }

        @Override
        public Query toQuery() {
            return query;
        }
    }

    static class WildcardParse implements QueryParse {
        private WildcardQuery wildcardQuery = null;

        //{wildcard:{filed:value}}
        @Override
        public QueryParse parse(JSONObject baseObject) {
            JSONObject wildcard_json = baseObject.getJSONObject("wildcard");
            if (wildcard_json == null || wildcard_json.isEmpty()) {
                return this;
            }
            for (Object key : wildcard_json.keySet()) {
                wildcardQuery = new WildcardQuery(new Term((String) key, (String) wildcard_json.get((String) key)));
            }

            return this;
        }

        @Override
        public Query toQuery() {
            return wildcardQuery;
        }
    }

    static class TextParse implements QueryParse {
        private BooleanQuery booleanQuery = null;

        @Override
        public QueryParse parse(JSONObject baseObject) {
            JSONObject termKeyValues = baseObject.getJSONObject("text");
            booleanQuery = null;
            if (termKeyValues == null || termKeyValues.isEmpty()) {
                return this;
            }
            booleanQuery = new BooleanQuery();
            String operator = JSONObjectUtils.getString(termKeyValues, "operator", "must").toUpperCase();
            termKeyValues.remove("operator");

            SearchContext searchContext = SearchContext.current();
            if (termKeyValues != null) {
                for (Object termField : termKeyValues.keySet()) {
                    String key = (String) termField;
                    String value = termKeyValues.getString(key);
                    if (value == null || value.isEmpty()) continue;
                    searchContext.texts(value);
                    if (key.contains(",")) {
                        String last_operator = JSONObjectUtils.getString(termKeyValues, "operator", "must").toUpperCase();
                        booleanQuery.add(parseMultiFieldText(key, value, BooleanClause.Occur.valueOf(last_operator)), BooleanClause.Occur.valueOf(operator));
                    } else {
                        booleanQuery.add(parseText(key, value), BooleanClause.Occur.valueOf(operator));
                    }
                }
//                String[] fields = new String[termKeyValues.size()];
//                termKeyValues.keySet().toArray(fields);
//                QueryParser queryParser = new MultiFieldQueryParser(Version.LUCENE_32,fields , AnalyzerHolder.whitespaceAnalyzer());
//                String  value = termKeyValues.getString(fields[0]);
//                                    queryParser.setDefaultOperator(QueryParser.Operator.AND);
//                                    Query query = null;
//                                    try {
//                                        searchContext.texts(value);
//                                        logger.info("QueryParser:"+queryParser.parse(AnalyzerHolder.defaultAnalyzer().toWhiteSpaceString(value)).toString());
//                                    } catch (Exception e) {
//                                        e.printStackTrace();
//                                    }
            }
            return this;
        }


        @Override
        public Query toQuery() {
            return optimize(booleanQuery);
        }
    }

    public static Query parseMultiFieldText(String field, String text, BooleanClause.Occur occur) {
        String[] fields = field.split(",");
        BooleanQuery finalQuery = new BooleanQuery();
        MMSegAnalyzer mmSegAnalyzer = (MMSegAnalyzer) analyzerService.defaultAnalyzer();
        String[] texts = mmSegAnalyzer.toWhiteSpaceString(text).split("\\s+");
        for (String term : texts) {
            BooleanQuery bq = new BooleanQuery();
            for (String _field : fields) {
                String _field_name = _field;
                float _boost = 1f;
                if (_field.contains("^")) {
                    String[] fields_and_boosts = _field.split("\\^");
                    _field_name = fields_and_boosts[0];
                    _boost = Float.parseFloat(fields_and_boosts[1]);
                }
                TermQuery termQuery = new TermQuery(new Term(_field_name, term));
                termQuery.setBoost(_boost);
                bq.add(termQuery, BooleanClause.Occur.SHOULD);
            }
            finalQuery.add(bq, occur);

        }
        return finalQuery;
    }


    public static Query parseText(String field, String text) {
        return parseText(field, text, BooleanClause.Occur.MUST);
    }

    public static Query parseText(String field, String text, BooleanClause.Occur occur) {
        BooleanQuery bq = new BooleanQuery();
        MMSegAnalyzer mmSegAnalyzer = (MMSegAnalyzer) analyzerService.defaultAnalyzer();
        String[] texts = mmSegAnalyzer.toWhiteSpaceString(text).split("\\s+");
        for (String term : texts) {
            TermQuery termQuery = new TermQuery(new Term(field, term));
            bq.add(termQuery, occur);
        }
        return bq;
    }

    //格式如  "filter":{"or":[{"term":{"status":0}},{"term":{"status":1}}]}
    static class OrParse extends BaseQueryParse {

        @Override
        public BooleanClause.Occur occur() {
            return BooleanClause.Occur.SHOULD;
        }

        @Override
        public Object preProcess(JSONObject baseObject) {

            if (baseObject.containsKey("or")) {
                shouldProcess = true;
            }
            return baseObject.get("or");
        }
    }

    //格式如  "filter":{"and":[{"term":{"status":0}},{"term":{"status":1}}]}
    static class AndParse extends BaseQueryParse {

        @Override
        public BooleanClause.Occur occur() {
            return BooleanClause.Occur.MUST;
        }

        @Override
        public Object preProcess(JSONObject baseObject) {
            if (baseObject.containsKey("and")) {
                shouldProcess = true;
            }
            return baseObject.get("and");
        }
    }

    //格式如  "filter":{"not":[{"term":{"status":0}},{"term":{"status":1}}]}
    static class NotParse extends BaseQueryParse {

        @Override
        public BooleanClause.Occur occur() {
            return BooleanClause.Occur.MUST_NOT;
        }

        @Override
        public Object preProcess(JSONObject baseObject) {
            if (baseObject.containsKey("not")) {
                shouldProcess = true;
            }
            return baseObject.get("not");
        }
    }

    static class BoolParse implements QueryParse {
        private BooleanQuery booleanQuery = null;

        @Override
        public QueryParse parse(JSONObject baseObject) {
            booleanQuery = null;

            if (!baseObject.containsKey("bool")) {
                return this;
            }
            booleanQuery = new BooleanQuery();
            JSONObject bool = baseObject.getJSONObject("bool");
            addFilter(bool, "must", "and");
            addFilter(bool, "should", "or");
            addFilter(bool, "must_not", "not");
            booleanQuery.setMinimumNumberShouldMatch(getInt(bool, "minimum_number_should_match", 1));
            booleanQuery.setBoost(getFloat(bool, "boost", 1.0f));
            return this;
        }

        private void addFilter(JSONObject bool, String name, String alias) {
            if (bool.containsKey(name)) {
                BaseQueryParse filterParser = new AndParse();
                JSONObject temp = new JSONObject();
                temp.put(alias, bool.get(name));
                Query tempQuery = filterParser.parse(temp).toQuery();
                if (tempQuery != null) {
                    booleanQuery.add(new BooleanClause(tempQuery, BooleanClause.Occur.valueOf(name.toUpperCase())));
                }
            }
        }

        @Override
        public Query toQuery() {
            return optimize(booleanQuery);
        }
    }


    static abstract class BaseQueryParse implements QueryParse {
        protected List<QueryParse> parsers = Lists.<QueryParse>newArrayList(new TermParse(), new RangeParse(), new TextParse());
        protected BooleanQuery booleanQuery = null;
        protected boolean shouldProcess = false;

        public QueryParse parse(JSONObject baseObject) {
            Object object = preProcess(baseObject);
            booleanQuery = null;
            if (!shouldProcess) {
                return this;
            }
            booleanQuery = new BooleanQuery();
            if (object instanceof JSONArray) {
                parseArray((JSONArray) object);
            } else if (object instanceof JSONObject) {
                parseHash((JSONObject) object);
            }
            return this;
        }

        /*       解析这种格式:[
        *            {
        *                "term" : { "tag" : "wow" }
        *            },
        *            {
        *                "term" : { "tag" : "elasticsearch" }
        *            }
        *        ]
        */
        protected void parseArray(JSONArray array) {
            for (int i = 0; i < array.size(); i++) {
                for (QueryParse filterParser : parsers) {
                    Query query = filterParser.parse(array.getJSONObject(i)).toQuery();
                    if (query != null) {
                        booleanQuery.add(new BooleanClause(query, occur()));
                    }
                }
            }
        }

        /*       解析这种格式
         *            { "term" : { "tag" : "wow" }，"range : { ..... }}
         *       首先转换成下面这种格式:
         *
         *
        *            [{
        *                "term" : { "tag" : "wow" }
        *            },
        *            {
        *                "range : { ..... }
        *            }
        *        ]
        *        再调用parseArray解析
        */
        protected void parseHash(JSONObject object) {
            Set<String> keys = object.keySet();
            JSONArray array = new JSONArray();
            for (String key : keys) {
                JSONObject temp = new JSONObject();
                temp.put(key, object.get(key));
                array.add(temp);
            }

            parseArray(array);
        }

        public abstract BooleanClause.Occur occur();

        public abstract Object preProcess(JSONObject baseObject);

        public Query toQuery() {
            return optimize(booleanQuery);
        }
    }

    private static Query optimize(Query query) {
        if (query == null) return null;
        if ((query instanceof BooleanQuery) && ((BooleanQuery) query).clauses().size() == 1) {
            return ((BooleanQuery) query).clauses().get(0).getQuery();
        }
        return query;
    }

}
