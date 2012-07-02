package net.csdn.context;

import com.google.common.collect.Lists;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.search.SearchResult;
import net.csdn.modules.transport.data.SearchHit;
import net.sf.json.JSONObject;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: william
 * Date: 11-9-6
 * Time: 下午5:18
 */
public class SearchContext {

    private static ThreadLocal<SearchContext> current = new ThreadLocal<SearchContext>();

    public static void setCurrent(SearchContext value) {
        current.set(value);
    }

    public static void removeCurrent() {
        current.remove();
    }

    public static SearchContext current() {
        return current.get();
    }


    private String name;
    private String[] types;
    private int from = 0;
    private int size = 10;
    private Sort sort = null;
    //new Sort(new SortField[]{SortField.FIELD_SCORE,new SortField("created_at",SortField.INT,true)});
    private Float minimumScore;
    private Query query;
    private Filter filter = null;
    private boolean topEnable;
    private SearchType searchType = SearchType.DEFAULT;

    private int total_fetch_size = 150;

    private JSONObject highlight;

    private List<String> fields;


    private RestRequest restRequest;

    private String originSource;

    private volatile int totalHits = 0;

    private String[] loadDocIds;
    private SearchResult searchResult = null;

    //需要分词的数据
    private List<String> texts = Lists.newArrayList();


    public SearchContext texts(String text) {
        texts.add(text);
        return this;
    }

//    public List<String> texts(){
//          return this.texts();
//    }

    public String texts() {
        StringBuffer stringBuffer = new StringBuffer();
        for (String str : texts) {
            stringBuffer.append(str + " ");
        }
        return stringBuffer.toString();
    }



    public int total_fetch_size() {
        return total_fetch_size;
    }


    public SearchContext total_fetch_size(int fetch_size) {
        this.total_fetch_size = fetch_size;
        return this;
    }


    public SearchContext searchResult(SearchResult _searchResult) {
        this.searchResult = _searchResult;
        this.totalHits(_searchResult.getTotal());
        return this;
    }


    public SearchType searchType() {
        return searchType;
    }

    public SearchContext searchType(SearchType searchType) {
        this.searchType = searchType;
        return this;
    }

    public SearchContext topEnable(boolean topEnable) {
        this.topEnable = topEnable;
        return this;
    }


    public JSONObject highlight() {
        return highlight;
    }

    public SearchContext highlight(JSONObject object) {
        this.highlight = object;
        return this;
    }

    public boolean topEnable() {
        return topEnable;
    }

    public SearchContext totalHits(int hits) {
        totalHits += hits;
        return this;
    }

    public SearchContext totalHitsDown(int hits) {
        totalHits -= hits;
        return this;
    }

    public int totalHits() {
        return totalHits;
    }


    public RestRequest restRequest() {
        return restRequest;
    }

    public SearchResult searchResult() {
        return searchResult;
    }

    public SearchContext fields(List<String> fields) {
        this.fields = fields;
        return this;
    }

    public List<String> fields() {
        return fields;
    }

    public List<SearchHit> cutQueryResult() {

        if (searchResult.getDatas().size() >= from) {
            int left = searchResult.getDatas().size() - from;
            searchResult.setDatas(searchResult.getDatas().subList(from, left > size ? from + size : from + left));
        }

        if (fields != null) {
            List<SearchHit> searchHits = searchResult.getDatas();
            for (SearchHit searchHit : searchHits) {
                Map<String, String> objectMap = new HashMap<String, String>();
                for (String field : fields) {
                    objectMap.put(field, searchHit.getObject().get(field));
                }
                searchHit.setObject(objectMap);
            }
        }

        return searchResult.getDatas();
    }


    public static class QUERY_FIRST_KEY_WORD {
        public final static String QUERY = "query";
        public final static String SIZE = "size";
        public final static String FROM = "from";
        public final static String FILTER = "filter";
        public final static String SORT = "sort";
        public final static String TOP = "top";
        public final static String Highlight = "highlight";
        public final static String Fields = "fields";
    }


    public SearchContext(String name, String[] types, RestRequest restRequest, String originSource) {
        this.name = name;
        this.originSource = originSource;
        this.restRequest = restRequest;
        this.types = types;
    }

    public SearchContext() {
        //only for test
    }


    public SearchContext filter(Filter filter) {
        this.filter = filter;
        return this;
    }

    public SearchContext query(Query query) {
        this.query = query;
        return this;
    }

    public SearchContext minimumScore(float minimumScore) {
        this.minimumScore = minimumScore;
        return this;
    }

    public SearchContext sort(Sort sort) {
        this.sort = sort;
        return this;
    }

    public SearchContext size(int size) {
        this.size = size;
        return this;
    }

    public SearchContext from(int from) {
        this.from = from;
        return this;
    }

    public SearchContext name(String name) {
        this.name = name;
        return this;
    }

    public SearchContext types(String[] types) {
        this.types = types;
        return this;
    }

    public String[] types() {
        return types;
    }

    public Filter filter() {
        return filter;
    }

    public String originSource() {
        return originSource;
    }

    public SearchContext originSource(String originSource) {
        this.originSource = originSource;
        return this;
    }

    public Query query() {
        return query;
    }

    public Float minimumScore() {
        return minimumScore;
    }

    public Sort sort() {
        return sort;
    }

    public int size() {
        return size;
    }

    public int from() {
        return from;
    }

    public String name() {
        return name;
    }

    public String[] loadDocIds() {
        return loadDocIds;
    }

    public SearchContext loadDocIds(String[] loadDocIds) {
        this.loadDocIds = loadDocIds;
        return this;
    }

}
