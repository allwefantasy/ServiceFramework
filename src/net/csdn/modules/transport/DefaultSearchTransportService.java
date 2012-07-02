package net.csdn.modules.transport;/**
 * User: WilliamZhu
 * Date: 12-5-31
 * Time: 上午10:50
 */

import com.google.inject.Inject;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.path.Url;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.support.HttpStatus;
import net.csdn.modules.search.SearchResult;
import net.csdn.modules.threadpool.ThreadPoolService;
import org.apache.lucene.index.ExtendedIndexSearcher;

import java.util.List;

import static net.csdn.common.logging.support.MessageFormat.format;

public class DefaultSearchTransportService implements SearchTransportService {
    private CSLogger logger = Loggers.getLogger(getClass());
    private CSLogger query_logger = Loggers.getLogger("query");

    @Inject
    private HttpTransportService httpTransportService;
    @Inject
    private ThreadPoolService threadPoolService;


    public void localSearch(SearchResult searchResult, List<ExtendedIndexSearcher> searchers, SearchCallBack searchCallBack) {
        try {
            searchCallBack.preProcess();
            for (ExtendedIndexSearcher searcher : searchers) {
                searchCallBack.process(searcher, searchResult);
            }
        } finally {
            searchCallBack.finishProcess();
        }
    }

    public void remoteSearch(SearchResult searchResult, List<Url> urls, String content, RemoteSearchCallBack searchCallBack) {

        //Now collect the results from other machines
        List<HttpTransportService.SResponse> responses = httpTransportService.asyncHttps(urls, content, RestRequest.Method.POST);


        for (HttpTransportService.SResponse response : responses) {
            try {
                if (response.getStatus() == HttpStatus.HttpStatusOK)
                    searchCallBack.process(response.json(), searchResult);
            } catch (Exception e) {
                logger.error(format(getClass().getName() + " error when parse data from url [{}]", response.getUrl()));
            }
        }

        searchCallBack.finishProcess();
    }


}
