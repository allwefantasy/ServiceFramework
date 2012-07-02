package net.csdn.modules.transport;

import net.csdn.common.path.Url;
import net.csdn.modules.search.SearchResult;
import net.sf.json.JSONObject;
import org.apache.lucene.index.ExtendedIndexSearcher;

import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-5-31
 * Time: 上午10:49
 */
public interface SearchTransportService {

    public void localSearch(SearchResult searchResult, List<ExtendedIndexSearcher> searchers, SearchCallBack searchCallBack);

    public void remoteSearch(SearchResult searchResult, List<Url> urls, String content, RemoteSearchCallBack searchCallBack);

    interface SearchCallBack {
        void preProcess();

        void process(ExtendedIndexSearcher searcher, SearchResult searchResult);

        void finishProcess();
    }

    interface RemoteSearchCallBack {
        void preProcess();

        void process(JSONObject object, SearchResult searchResult);

        void finishProcess();
    }
}
