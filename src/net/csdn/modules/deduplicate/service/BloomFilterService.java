package net.csdn.modules.deduplicate.service;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;
import net.csdn.common.settings.Settings;
import net.csdn.modules.analyzer.AnalyzerService;
import net.csdn.modules.analyzer.mmseg4j.analysis.MMSegAnalyzer;
import net.csdn.modules.deduplicate.algorithm.bloomfilter.Algorithm;
import net.csdn.modules.deduplicate.algorithm.bloomfilter.FixSizedBloomFilter;
import net.csdn.modules.threadpool.ThreadPoolService;
import net.csdn.modules.transport.data.SearchHit;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * User: william
 * Date: 12-4-13
 * Time: 下午2:25
 */
public class BloomFilterService {

    private ThreadPoolService threadPoolService;
    private AnalyzerService analyzerService;
    private Settings settings;

    private final int thresh_hold = 60;

    private List<FixSizedBloomFilter> list = new ArrayList<FixSizedBloomFilter>();
    private Map<Integer, List<FixSizedBloomFilter>> prefix_temp = new HashMap<Integer, List<FixSizedBloomFilter>>(thresh_hold);
    private List<FixSizedBloomFilter> results = new ArrayList<FixSizedBloomFilter>();

    @Inject
    public BloomFilterService(ThreadPoolService threadPoolService, AnalyzerService analyzerService, Settings settings, @Assisted List<SearchHit> searchHits) {
        this.threadPoolService = threadPoolService;
        this.analyzerService = analyzerService;
        this.settings = settings;
        transform(searchHits);
        sort();
    }


    private BloomFilterService sort() {
        Collections.sort(list, Algorithm.comparator);
        normalize_datas();
        return this;
    }

    private BloomFilterService transform(SearchHit searchHit) {
        String content = searchHit.getObject().get("title") + " " + searchHit.getObject().get("body");
        int doc = Integer.parseInt(searchHit.get_id());
        String[] contentChunks = Algorithm.chunking((MMSegAnalyzer) analyzerService.defaultAnalyzer(), content);
        if (contentChunks.length < 50) return this;
        FixSizedBloomFilter<SearchHit> fixSizedBloomFilter = new FixSizedBloomFilter(contentChunks, doc, searchHit);
        list.add(fixSizedBloomFilter);
        return this;
    }

    private BloomFilterService transform(List<SearchHit> searchHits) {
        for (SearchHit searchHit : searchHits) {
            transform(searchHit);
        }
        return this;
    }


    private BloomFilterService similarity() {
        Set<FixSizedBloomFilter> fixSizedBloomFilters = new HashSet<FixSizedBloomFilter>(list);
        Iterator<FixSizedBloomFilter> itr = fixSizedBloomFilters.iterator();
        while (itr.hasNext()) {
            FixSizedBloomFilter fixSizedBloomFilter = itr.next();
            Set<FixSizedBloomFilter> temp = inner_similarity(fixSizedBloomFilter);
            fixSizedBloomFilters.remove(fixSizedBloomFilter);
            results.add(fixSizedBloomFilter);
            if (temp != null && temp.size() > 0) {
                fixSizedBloomFilters.removeAll(temp);
            }
            itr = fixSizedBloomFilters.iterator();
        }
        return this;
    }

    private void normalize_datas() {

        int len = list.size();
        for (int k = 0; k < thresh_hold; k++) {

            List<FixSizedBloomFilter> temp = new ArrayList(len);
            for (int j = 0; j < len; j++) {
                temp.add(new FixSizedBloomFilter(Algorithm.substituteBits(list.get(j).bitSet, k, thresh_hold), j));
            }
            prefix_temp.put(k, temp);

        }
    }


    public BloomFilterService result(List<SearchHit> searchHits) {
        similarity();
        if (results.size() == 0) return this;
        searchHits.clear();
        for (FixSizedBloomFilter<SearchHit> fixSizedBloomFilter : results) {
            searchHits.add(fixSizedBloomFilter.searchHit);
        }

        return this;
    }


    private Set<FixSizedBloomFilter> inner_similarity(final FixSizedBloomFilter<SearchHit> fixSizedBloomFilter) {


        final Set<FixSizedBloomFilter> list_copy = new HashSet<FixSizedBloomFilter>();
        List<FutureTask<Set<FixSizedBloomFilter>>> futureTasks = new ArrayList<FutureTask<Set<FixSizedBloomFilter>>>();

        final List<FixSizedBloomFilter> blocks = new ArrayList<FixSizedBloomFilter>();
        for (Map.Entry<Integer, List<FixSizedBloomFilter>> entry : prefix_temp.entrySet()) {
            blocks.add(new FixSizedBloomFilter(Algorithm.substituteBits(fixSizedBloomFilter.bitSet, entry.getKey(), thresh_hold)));
        }


        FutureTask<Set<FixSizedBloomFilter>> futureTask = new FutureTask<Set<FixSizedBloomFilter>>(new Callable<Set<FixSizedBloomFilter>>() {
            @Override
            public Set<FixSizedBloomFilter> call() throws Exception {
                Set<FixSizedBloomFilter> map_temp_list = new HashSet<FixSizedBloomFilter>();
                Set<FixSizedBloomFilter> result = new HashSet<FixSizedBloomFilter>();

                int i = 0;
                for (FixSizedBloomFilter target_prefix : blocks) {
                    List<FixSizedBloomFilter> temp_list = Algorithm.binarySearch(prefix_temp.get(i++), target_prefix, Algorithm.comparator);
                    for (FixSizedBloomFilter fixSizedBloomFilter1 : temp_list) {
                        map_temp_list.add(list.get(fixSizedBloomFilter1.doc));
                    }
                }
                System.out.println(map_temp_list.size());
                for (FixSizedBloomFilter really_fsb : map_temp_list) {
                    if (fixSizedBloomFilter.doc == really_fsb.doc) continue;
                    if (Algorithm.hammingDistanceSimilarity(really_fsb.bitSet, fixSizedBloomFilter.bitSet) < thresh_hold) {
                        result.add(really_fsb);
                    }
                }

                return result;
            }
        });
        threadPoolService.executor(ThreadPoolService.Names.CACHED).execute(futureTask);
        futureTasks.add(futureTask);

        for (FutureTask<Set<FixSizedBloomFilter>> futureTaskTemp : futureTasks) {
            try {
                list_copy.addAll(futureTaskTemp.get());
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        return list_copy;
    }


}
