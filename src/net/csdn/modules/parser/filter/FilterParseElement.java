package net.csdn.modules.parser.filter;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import net.csdn.context.SearchContext;
import net.csdn.modules.analyzer.AnalyzerService;
import net.csdn.modules.parser.query.QueryParseElement;
import net.csdn.modules.parser.query.SearchParseElement;
import net.csdn.modules.gateway.GatewayData;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.lucene.index.Engine;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static net.csdn.modules.http.JSONObjectUtils.*;

/**
 * User: william
 * Date: 11-9-22
 * Time: 下午3:55
 */
public class FilterParseElement implements SearchParseElement {


    @Inject
    private static GatewayData gatewayData;
    @Inject
    private static AnalyzerService analyzerService;

    /*   filter:{"bool" : {
       "must" : {
           "term" : { "user" : "kimchy" }
       },
       "must_not" : {
           "range" : {
               "age" : { "from" : 10, "to" : 20 }
           }
       },
       "should" : [
           {
               "term" : { "tag" : "wow" },
           },
           {
               "term" : { "tag" : "elasticsearch" }
           }
       ],
   }
}     */
    @Override
    public void parse(JSONObject obj, SearchContext searchContext) {
        List<FilterParser> parserList = Lists.newArrayList(new BoolParse(), new AndParse(), new OrParse(), new NotParse(), new TermParse(), new RangeParse(), new DuplicateParse());
        obj = getJSONObject(obj, SearchContext.QUERY_FIRST_KEY_WORD.FILTER);

        BooleanFilter booleanFilter = new BooleanFilter();
//        String[] types = searchContext.types();
//        TermsFilter tf = new TermsFilter();
//        for (String type : types) {
//            tf.addTerm(new Term("_type", type));
//        }
//        booleanFilter.add(new FilterClause(tf, BooleanClause.Occur.MUST));
        if (obj == null || obj.isEmpty()) {
            return;
        }
        ;
        int filterCount = 0;
        //解析之后的最终filter
        for (FilterParser filterParser : parserList) {
            Filter tempFilter = filterParser.parse(obj).toFilter();
            if (tempFilter != null) {
                booleanFilter.add(new FilterClause(tempFilter, BooleanClause.Occur.MUST));
                filterCount++;
            }
        }
        if (filterCount == 0) {
            return;
        }
        searchContext.filter(booleanFilter);
    }

    //格式如  "filter":{"or":[{"term":{"status":0}},{"term":{"status":1}}]}
    static class OrParse extends BaseFilterParse {

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
    static class AndParse extends BaseFilterParse {

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
    static class NotParse extends BaseFilterParse {

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

    static class BoolParse implements FilterParser {
        protected BooleanFilter booleanFilter = null;

        @Override
        public FilterParser parse(JSONObject baseObject) {
            booleanFilter = null;
            if (!baseObject.containsKey("bool")) {
                return this;
            }
            booleanFilter = new BooleanFilter();
            JSONObject bool = baseObject.getJSONObject("bool");
            addFilter(bool, "must", "and");
            addFilter(bool, "should", "or");
            addFilter(bool, "must_not", "not");
            return this;
        }

        private void addFilter(JSONObject bool, String name, String alias) {
            if (bool.containsKey(name)) {
                FilterParser filterParser = null;
                if (name.equals("must")) {
                    filterParser = new AndParse();
                }
                if (name.equals("should")) {
                    filterParser = new OrParse();
                }
                if (name.equals("must_not")) {
                    filterParser = new NotParse();
                }
                JSONObject temp = new JSONObject();
                temp.put(alias, bool.get(name));
                Filter tempFilter = filterParser.parse(temp).toFilter();
                if (tempFilter != null) {
                    booleanFilter.add(new FilterClause(tempFilter, BooleanClause.Occur.valueOf(name.toUpperCase())));
                }
            }
        }

        @Override
        public Filter toFilter() {
            return booleanFilter;
        }
    }

    static abstract class BaseFilterParse implements FilterParser {
        protected List<FilterParser> parsers = Lists.<FilterParser>newArrayList(new TermParse(), new RangeParse());
        protected BooleanFilter booleanFilter = null;
        protected boolean shouldProcess = false;

        public FilterParser parse(JSONObject baseObject) {
            Object object = preProcess(baseObject);
            booleanFilter = null;
            if (!shouldProcess) {
                return this;
            }
            booleanFilter = new BooleanFilter();
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
                for (FilterParser filterParser : parsers) {
                    Filter filter = filterParser.parse(array.getJSONObject(i)).toFilter();
                    if (filter != null) {
                        booleanFilter.add(new FilterClause(filter, occur()));
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

        public Filter toFilter() {
            return booleanFilter;
        }
    }


    interface FilterParser {
        public FilterParser parse(JSONObject baseObject);

        public Filter toFilter();
    }


    static class TermParse implements FilterParser {
        private TermsFilter termFilter = null;

        public FilterParser parse(JSONObject baseObject) {
            termFilter = null;
            JSONObject termJSON = getJSONObject(baseObject, "term", getJSONObject(baseObject, "field"));
            if (termJSON == null || termJSON.isEmpty()) {
                return this;
            }
            termFilter = new TermsFilter();
            for (Iterator i = termJSON.keys(); i.hasNext(); ) {
                String key = (String) i.next();
                String value = termJSON.getString(key);
                termFilter.addTerm(new Term(key, QueryParseElement.termConvertor(key, value)));
            }
            return this;
        }

        public Filter toFilter() {
            return termFilter;
        }

    }


    /*
      "duplicate" : "field_name"

    */
    static class DuplicateParse implements FilterParser {
        private DuplicateFilter duplicateFilter = null;

        @Override
        public FilterParser parse(JSONObject baseObject) {
            String duplicate_filed = getString(baseObject, "duplicate");
            if (duplicate_filed == null) return this;
            duplicateFilter = new DuplicateFilter(duplicate_filed, DuplicateFilter.KM_USE_FIRST_OCCURRENCE, DuplicateFilter.PM_FAST_INVALIDATION);
            return this;
        }

        @Override
        public Filter toFilter() {
            return duplicateFilter;
        }
    }


    /*
      "range" : {
          "age" : { "from" : 10, "to" : 20 ,"include_lower" : true,
      "include_upper": false}
      }

    */
    static class RangeParse implements FilterParser {
        private Filter filter = null;

        public FilterParser parse(JSONObject baseObject) {
            filter = null;
            JSONObject termKeyValues = baseObject.getJSONObject("range");
            if (termKeyValues == null || termKeyValues.isEmpty()) return this;
            String rangeField = (String) termKeyValues.keys().next();
            JSONObject obj = termKeyValues.getJSONObject(rangeField);

            SearchContext searchContext = SearchContext.current();
            Engine.Mapper mapper = gatewayData.mapper(searchContext.name(), searchContext.types()[0]);
            Engine.MapperType mapperType = mapper._properties(rangeField);
            if (mapperType.type().equals("integer"))
                filter = NumericRangeFilter.newIntRange(rangeField, getInt(obj, "from"), getInt(obj, "to"), getBoolean(obj, "include_lower"), getBoolean(obj, "include_upper"));
            else if (mapperType.type().equals("float"))
                filter = NumericRangeFilter.newFloatRange(rangeField, getFloat(obj, "from"), getFloat(obj, "to"), getBoolean(obj, "include_lower"), getBoolean(obj, "include_upper"));
            else if (mapperType.type().equals("double"))
                filter = NumericRangeFilter.newDoubleRange(rangeField, getDouble(obj, "from"), getDouble(obj, "to"), getBoolean(obj, "include_lower"), getBoolean(obj, "include_upper"));
            else if (mapperType.type().equals("long"))
                filter = NumericRangeFilter.newLongRange(rangeField, getLong(obj, "from"), getLong(obj, "to"), getBoolean(obj, "include_lower"), getBoolean(obj, "include_upper"));
            else {
                filter = new TermRangeFilter(rangeField, getString(obj, "from"), getString(obj, "to"), getBoolean(obj, "include_lower"), getBoolean(obj, "include_upper"));
            }
            return this;

        }

        public Filter toFilter() {
            return filter;
        }
    }

}
