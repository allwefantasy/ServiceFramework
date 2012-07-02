package net.csdn.modules.search;/**
 * User: WilliamZhu
 * Date: 12-5-31
 * Time: 下午2:39
 */

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import net.csdn.cluster.routing.Shard;
import net.csdn.common.logging.CSLogger;
import net.csdn.common.logging.Loggers;
import net.csdn.common.path.Url;
import net.csdn.context.SearchContext;
import net.csdn.modules.gateway.GatewayData;
import net.csdn.modules.transport.SearchTransportService;
import net.csdn.modules.transport.data.DefaultDataParserService;
import net.csdn.modules.transport.data.SearchHit;
import net.sf.json.JSONObject;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldSelector;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.ExtendedIndexSearcher;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

public class DefaultSearchService implements SearchService {
    private CSLogger logger = Loggers.getLogger(getClass());
    @Inject
    private SearchTransportService searchTransportService;

    @Inject
    private GatewayData gatewayData;

    @Inject
    private DefaultDataParserService dataParserService;

    public List<ExtendedIndexSearcher> searchers(final String index) {
        List<ExtendedIndexSearcher> searcherList = Lists.newArrayList();
        List<Shard> shards = gatewayData.routing(index).shards();
        for (Shard shard : shards) {
            if (shard.local())
                searcherList.add(shard.engineFutureTask().readerSearcherHolder().searcher());
        }
        return searcherList;

    }

    public void search2(SearchResult searchResult, FieldSelector commonFieldSelector) {
        List<SearchHit> searchHits = searchResult.getDatas();
        for (SearchHit searchHit : searchHits) {
            try {
                Document document = null;
                if (commonFieldSelector == null) {
                    document = searchHit.extendedIndexSearcher.doc(searchHit.getDoc());
                } else {
                    document = searchHit.extendedIndexSearcher.doc(searchHit.getDoc(), commonFieldSelector);
                }
                //这个字段是必须(this field is required)
                String uid = document.get("_uid");
                searchHit.set_uid(uid);
                String[] s_uid = uid.split("#");
                searchHit.set_type(s_uid[0]);
                searchHit.set_id(s_uid[1]);

                //field in fieldSelector and field marked as stored will be add to searchHit
                List<Fieldable> fields = document.getFields();
                Map<String, String> obj = searchHit.getObject();
                for (Fieldable fieldable : fields) {
                    if (fieldable.isStored()) {
                        String wow = document.get(fieldable.name());
                        if (wow != null) {
                            obj.put(fieldable.name(), wow);
                        }
                    }
                }

            } catch (IOException e) {
                logger.error("error when fetch document from index==>" + e.getMessage());
            }
        }
    }

    @Override
    public List<Map> search3(SearchResult searchResult, SFieldSelector fieldSelector) {
        List<Map> maps = new ArrayList<Map>(searchResult.getDatas().size());
        List<SearchHit> searchHits = searchResult.getDatas();
        for (SearchHit searchHit : searchHits) {
            Field[] fields = SearchHit.class.getDeclaredFields();
            Map map = new HashMap();
            for (Field field : fields) {
                if (fieldSelector.accept(field.getName())) {
                    field.setAccessible(true);
                    try {
                        map.put(field.getName(), field.get(searchHit));
                    } catch (IllegalAccessException e) {

                    }

                }
            }
            maps.add(map);

        }
        return maps;
    }


    public SearchResult search(String index, final Query query, final int fetch_size) {
        SearchResult searchResult = new SearchResult(new ArrayList<SearchHit>(), 0, fetch_size);
        if (query == null || !shouldSearchLocal(index)) return searchResult;
        searchTransportService.localSearch(searchResult, searchers(index), new SearchTransportService.SearchCallBack() {
            @Override
            public void preProcess() {
            }

            @Override
            public void process(ExtendedIndexSearcher searcher, SearchResult searchResult) {
                try {
                    toSearchHits(searchResult, searcher.search(query, fetch_size), searcher);
                } catch (IOException e) {
                    e.printStackTrace();
                    logger.error("error when extendedIndexSearcher to search index ==>" + e.getMessage());
                }
            }

            @Override
            public void finishProcess() {
            }
        });

        return searchResult;

    }


    public SearchResult search(String index, final Query query, final int fetch_size, final Sort sort) {
        SearchResult searchResult = new SearchResult(new ArrayList<SearchHit>(), 0, fetch_size);
        if (query == null || !shouldSearchLocal(index)) return searchResult;
        searchTransportService.localSearch(searchResult, searchers(index), new SearchTransportService.SearchCallBack() {
            @Override
            public void preProcess() {
            }

            @Override
            public void process(ExtendedIndexSearcher searcher, SearchResult searchResult) {
                try {
                    toSearchHits(searchResult, searcher.search(query, fetch_size, sort), searcher);
                } catch (IOException e) {
                    e.printStackTrace();
                    logger.error("error when extendedIndexSearcher to search index ==>" + e.getMessage());
                }
            }

            @Override
            public void finishProcess() {
            }
        });

        return searchResult;

    }

    public SearchResult search(String index, final Query query, final Filter filter, final Collector collector) {
        SearchResult searchResult = new SearchResult(new ArrayList<SearchHit>(), 0, 0);
        if (query == null || !shouldSearchLocal(index)) return searchResult;
        searchTransportService.localSearch(searchResult, searchers(index), new SearchTransportService.SearchCallBack() {
            @Override
            public void preProcess() {
            }

            @Override
            public void process(ExtendedIndexSearcher searcher, SearchResult searchResult) {
                try {
                    searcher.search(query, filter, collector);
                } catch (IOException e) {

                }

            }

            @Override
            public void finishProcess() {
            }
        });

        return searchResult;

    }

    @Override
    public SearchResult search(String index, final Query query, final Collector collector) {
        SearchResult searchResult = new SearchResult(new ArrayList<SearchHit>(), 0, 0);
        if (query == null || !shouldSearchLocal(index)) return searchResult;
        searchTransportService.localSearch(searchResult, searchers(index), new SearchTransportService.SearchCallBack() {
            @Override
            public void preProcess() {
            }

            @Override
            public void process(ExtendedIndexSearcher searcher, SearchResult searchResult) {
                TopDocs topDocs = null;
                try {
                    searcher.search(query, collector);

                } catch (IOException e) {

                }

            }

            @Override
            public void finishProcess() {
            }
        });

        return searchResult;
    }

    public SearchResult search(String index, final Query query, final Filter filter, final int fetch_size, final Sort sort) {
        SearchResult searchResult = new SearchResult(new ArrayList<SearchHit>(), 0, fetch_size);
        if (query == null || !shouldSearchLocal(index)) return searchResult;
        searchTransportService.localSearch(searchResult, searchers(index), new SearchTransportService.SearchCallBack() {
            @Override
            public void preProcess() {
            }

            @Override
            public void process(ExtendedIndexSearcher searcher, SearchResult searchResult) {
                try {
                    toSearchHits(searchResult, searcher.search(query, filter, fetch_size, sort), searcher);
                } catch (IOException e) {
                    e.printStackTrace();
                    logger.error("error when extendedIndexSearcher to search index ==>" + e.getMessage());
                }
            }

            @Override
            public void finishProcess() {
            }
        });

        return searchResult;

    }

    public SearchResult search(String index, final Query query, final Filter filter, final int fetch_size) {
        SearchResult searchResult = new SearchResult(new ArrayList<SearchHit>(), 0, fetch_size);
        if (query == null || !shouldSearchLocal(index)) return searchResult;
        searchTransportService.localSearch(searchResult, searchers(index), new SearchTransportService.SearchCallBack() {
            @Override
            public void preProcess() {
            }

            @Override
            public void process(ExtendedIndexSearcher searcher, SearchResult searchResult) {
                try {
                    toSearchHits(searchResult, searcher.search(query, filter, fetch_size), searcher);
                } catch (IOException e) {
                    e.printStackTrace();
                    logger.error("error when extendedIndexSearcher to search index ==>" + e.getMessage());
                }

            }

            @Override
            public void finishProcess() {
            }
        });

        return searchResult;

    }


    public SearchResult countSearch(String index, final Query query) {
        return countSearch(searchers(index), query, null);

    }

    @Override
    public SearchResult remoteSearch(List<Url> urls, String query, SearchResult searchResult) {
        if (urls.size() == 0) return searchResult;
        searchTransportService.remoteSearch(searchResult, urls, query, new SearchTransportService.RemoteSearchCallBack() {
            @Override
            public void preProcess() {
            }

            @Override
            public void process(JSONObject object, SearchResult searchResult) {
                SearchResult tempSearchResult = dataParserService.toObject(object);
                searchResult.merge(tempSearchResult);
            }

            @Override
            public void finishProcess() {
            }
        });
        return searchResult;
    }

    @Override
    public SearchResult localSearch(SearchContext context) {
        if (context.filter() == null && context.sort() == null)
            return search(context.name(), context.query(), context.total_fetch_size());
        else if (context.filter() != null && context.sort() == null) {
            return search(context.name(), context.query(), context.filter(), context.total_fetch_size());
        } else if (context.filter() == null && context.sort() != null) {
            return search(context.name(), context.query(), context.total_fetch_size(), context.sort());
        } else {
            return search(context.name(), context.query(), context.filter(), context.total_fetch_size(), context.sort());
        }
    }

    @Override
    public boolean shouldSearchLocal(String indexName) {
        List<Shard> shards = gatewayData.routing(indexName).shards();
        for (Shard shard : shards) {
            if (shard.local()) {
                return true;
            }
        }
        return false;
    }

    public SearchResult countSearch(List<ExtendedIndexSearcher> searchers, final Query query, final Filter filter) {
        if (query == null) return null;
        SearchResult searchResult = new SearchResult(new ArrayList<SearchHit>(), 0, 0);
        searchTransportService.localSearch(searchResult, searchers, new SearchTransportService.SearchCallBack() {
            @Override
            public void preProcess() {
            }

            @Override
            public void process(ExtendedIndexSearcher searcher, SearchResult searchResult) {
                try {
                    CountCollector countCollector = new CountCollector();
                    if (filter == null)
                        searcher.search(query, countCollector);
                    else
                        searcher.search(query, filter, countCollector);
                    searchResult.total(countCollector.totalHits);
                } catch (IOException e) {
                    e.printStackTrace();
                    logger.error("error when extendedIndexSearcher to search index ==>" + e.getMessage());
                }

            }

            @Override
            public void finishProcess() {
            }
        });
        return searchResult;

    }

    private void toSearchHits(SearchResult searchResult, TopDocs topDocs, ExtendedIndexSearcher extendedIndexSearcher) {
        searchResult.total(topDocs.totalHits);
        for (ScoreDoc doc : topDocs.scoreDocs) {
            SearchHit searchHit = null;
            if (doc instanceof FieldDoc) {
                FieldDoc fieldDoc = (FieldDoc) doc;
                searchHit = new SearchHit(fieldDoc.fields, fieldDoc.doc, fieldDoc.score, false, null, extendedIndexSearcher.shard().index(), extendedIndexSearcher.shard());
            } else {
                searchHit = new SearchHit(new Comparable[0], doc.doc, doc.score, false, null, extendedIndexSearcher.shard().index(), extendedIndexSearcher.shard());

            }
            searchHit.extendedIndexSearcher = extendedIndexSearcher;
            searchResult.datas(searchHit);

        }
    }


    static class CountCollector extends Collector {

        private int totalHits = 0;

        @Override
        public void setScorer(Scorer scorer) throws IOException {
        }

        @Override
        public void collect(int doc) throws IOException {
            totalHits++;
        }

        @Override
        public void setNextReader(IndexReader reader, int docBase) throws IOException {
        }

        @Override
        public boolean acceptsDocsOutOfOrder() {
            return true;
        }

        public TopDocs topDocs() {
            return new TopDocs(totalHits, EMPTY, 0);
        }

        private static ScoreDoc[] EMPTY = new ScoreDoc[0];
    }

    static class ScanCollector extends Collector {

        private final int from;

        private final int to;

        private final ArrayList<ScoreDoc> docs;

        private int docBase;

        private int counter;

        ScanCollector(int from, int size) {
            this.from = from;
            this.to = from + size;
            this.docs = new ArrayList<ScoreDoc>(size);
        }

        public TopDocs topDocs() {
            return new TopDocs(docs.size(), docs.toArray(new ScoreDoc[docs.size()]), 0f);
        }

        @Override
        public void setScorer(Scorer scorer) throws IOException {
        }

        @Override
        public void collect(int doc) throws IOException {
            if (counter >= from) {
                docs.add(new ScoreDoc(docBase + doc, 0f));
            }
            counter++;
            if (counter >= to) {
                throw StopCollectingException;
            }
        }

        @Override
        public void setNextReader(IndexReader indexReader, int docBase) throws IOException {
            this.docBase = docBase;
        }

        @Override
        public boolean acceptsDocsOutOfOrder() {
            return true;
        }

        public static final RuntimeException StopCollectingException = new StopCollectingException();

        static class StopCollectingException extends RuntimeException {
            @Override
            public Throwable fillInStackTrace() {
                return null;
            }
        }
    }
}
