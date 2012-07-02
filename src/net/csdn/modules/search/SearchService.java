package net.csdn.modules.search;/**
 * User: WilliamZhu
 * Date: 12-5-31
 * Time: 下午2:39
 */

import net.csdn.common.path.Url;
import net.csdn.context.SearchContext;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.index.ExtendedIndexSearcher;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;

import java.util.List;
import java.util.Map;

public interface SearchService {
    public SearchResult search(String index, final Query query, final int fetch_size, final Sort sort);

    public SearchResult search(String index, final Query query, final Filter filter, final Collector collector);

    public SearchResult search(String index, final Query query, final Collector collector);

    public SearchResult search(String index, final Query query, final Filter filter, final int fetch_size, final Sort sort);

    public SearchResult search(String index, final Query query, final Filter filter, final int fetch_size);


    public SearchResult search(String index, final Query query, final int fetch_size);

    public SearchResult countSearch(String index, final Query query);

    public SearchResult remoteSearch(List<Url> urls, String query, SearchResult searchResult);

    public SearchResult localSearch(SearchContext searchContext);

    public boolean shouldSearchLocal(String indexName);

    public void search2(SearchResult searchResult, FieldSelector commonFieldSelector);

    public List<Map> search3(SearchResult searchResult, SFieldSelector fieldSelector);

    interface SFieldSelector {
        boolean accept(String value);
    }

}
