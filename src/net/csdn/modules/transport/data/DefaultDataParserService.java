package net.csdn.modules.transport.data;/**
 * User: WilliamZhu
 * Date: 12-5-31
 * Time: 下午1:46
 */

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import net.csdn.modules.compress.gzip.GZip;
import net.csdn.modules.search.SearchResult;
import net.csdn.modules.search.SearchService;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.lucene.search.SortField;

import java.util.*;

import static net.csdn.common.collections.WowCollections.getInt;
import static net.csdn.modules.http.JSONObjectUtils.getJSONArray;

public class DefaultDataParserService {

    @Inject
    private SearchService searchService;

    public SearchResult toObject(JSONObject object) {

        JSONArray datas = getJSONArray(object, "datas");
        List<SearchHit> searchHits = new ArrayList<SearchHit>(datas.size());

        for (int i = 0; i < datas.size(); i++) {
            JSONObject obj = datas.getJSONObject(i);
            SearchHit docWrapper = (SearchHit) JSONObject.toBean(obj, SearchHit.class);
            searchHits.add(docWrapper);
        }
        return new SearchResult(searchHits, getInt(object, "total"), 50);
    }


    public SearchResult forServer(final SearchResult searchResult) {
        return searchResult;
    }

    public String forServerAsString(final SearchResult searchResult) {
        return JSONObject.fromObject(forServer(searchResult)).toString();
    }

    public Map forClient(final SearchResult searchResult) {
        Map maps = Maps.newHashMap();

        maps.put("hits", searchService.search3(searchResult, new SearchService.SFieldSelector() {
            private String[] fields = new String[]{"object", "_type", "_id", "_index", "fields"};

            @Override
            public boolean accept(String value) {
                for (String str : fields) {
                    if (str.equals(value)) return true;
                }
                return false;
            }
        }));
        maps.put("total", searchResult.getTotal());
        return maps;
    }


    public String forClientAsString(final SearchResult searchResult) {
        return JSONObject.fromObject(forClient(searchResult)).toString();
    }


    public void sort(List<SearchHit> list) {
        List topDocs = Lists.newArrayList();
        List normalDocs = Lists.newArrayList();
        for (SearchHit searchHit : list) {
            if (searchHit.isTopHit()) {
                topDocs.add(searchHit);
            } else {
                normalDocs.add(searchHit);
            }
        }
        innerScoreSort(topDocs);
        innerScoreSort(normalDocs);
        topDocs.addAll(normalDocs);
        list.clear();
        list.addAll(topDocs);
    }


    public void sort(List<SearchHit> list, final SortField[] fields) {
        List topDocs = Lists.newArrayList();
        List normalDocs = Lists.newArrayList();
        for (SearchHit searchHit : list) {
            if (searchHit.isTopHit()) {
                topDocs.add(searchHit);
            } else {
                normalDocs.add(searchHit);
            }
        }
        innerFieldSort(topDocs, fields);
        innerFieldSort(normalDocs, fields);
        topDocs.addAll(normalDocs);
        list.clear();
        list.addAll(topDocs);
    }

    private void innerScoreSort(List<SearchHit> list) {
        Collections.sort(list, new Comparator<SearchHit>() {
            @Override
            public int compare(SearchHit docWrapperA, SearchHit docWrapperB) {
                double temp = docWrapperA.getScore() - docWrapperB.getScore();
                return temp == 0 ? 0 : (temp > 0 ? -1 : 1);
            }
        });
    }

    private void innerFieldSort(List<SearchHit> list, final SortField[] fields) {
        Collections.sort(list, new Comparator<SearchHit>() {
            @Override
            public int compare(SearchHit docWrapperA, SearchHit docWrapperB) {
                final int n = fields.length;
                int c = 0;
                for (int i = 0; i < n && c == 0; ++i) {
                    final int type = fields[i].getType();
                    if (type == SortField.STRING) {
                        final String s1 = (String) docWrapperA.getFields()[i];
                        final String s2 = (String) docWrapperB.getFields()[i];
                        // null values need to be sorted first, because of how FieldCache.getStringIndex()
                        // works - in that routine, any documents without a value in the given field are
                        // put first.  If both are null, the next SortField is used
                        if (s1 == null) {
                            c = (s2 == null) ? 0 : -1;
                        } else if (s2 == null) {
                            c = 1;
                        } else if (fields[i].getLocale() == null) {
                            c = s1.compareTo(s2);
                        }
                    } else {
                        c = docWrapperA.getFields()[i].compareTo(docWrapperB.getFields()[i]);
                    }
                    // reverse sort
                    if (fields[i].getReverse()) {
                        c = -c;
                    } else if (fields[i].getType() == SortField.SCORE) {
                        c = -c;
                    }
                }
                //如果所有的域都无法相同，那么只能根据doc进行比较了
                if (c == 0) {
                    return docWrapperA.getDoc() - docWrapperB.getDoc();
                }
                return c;
            }
        });
    }

}
