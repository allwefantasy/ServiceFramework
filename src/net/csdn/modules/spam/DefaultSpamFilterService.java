package net.csdn.modules.spam;

import com.google.inject.Inject;
import net.csdn.common.io.Streams;
import net.csdn.env.Environment;
import net.csdn.modules.analyzer.AnalyzerService;
import net.csdn.modules.analyzer.mmseg4j.analysis.MMSegAnalyzer;
import net.csdn.modules.deduplicate.algorithm.bloomfilter.Algorithm;
import net.csdn.modules.deduplicate.algorithm.bloomfilter.FixSizedBloomFilter;
import net.csdn.modules.threadpool.ThreadPoolService;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/**
 * User: WilliamZhu
 * Date: 12-6-12
 * Time: 下午11:01
 */
public class DefaultSpamFilterService implements SpamFilterService {

    private Environment environment;
    private AnalyzerService analyzerService;
    private ThreadPoolService threadPoolService;

    private boolean enable = true;
    private int thresh_hold = 60;
    private List<FixSizedBloomFilter> spamM = new ArrayList<FixSizedBloomFilter>();
    private Map<Integer, List<FixSizedBloomFilter>> prefix_temp = new HashMap<Integer, List<FixSizedBloomFilter>>(thresh_hold);

    @Inject
    public DefaultSpamFilterService(Environment environment, AnalyzerService analyzerService, ThreadPoolService threadPoolService) {
        this.environment = environment;
        this.analyzerService = analyzerService;
        this.threadPoolService = threadPoolService;
        File dic = environment.dictionariesFile();
        File spamMatirials = new File(dic.getPath() + "/spam");
        //如果没有语料，我们将禁用垃圾过滤功能
        if (!spamMatirials.exists() || spamMatirials.listFiles() == null || spamMatirials.listFiles().length < 3) {
            enable = false;
            return;
        }
        //语料格式为id为文件名，里面为正文内容
        for (File file : spamMatirials.listFiles()) {
            try {
                String body = Streams.copyToString(new FileReader(file));
                spamM.add(new FixSizedBloomFilter(Algorithm.chunking((MMSegAnalyzer) analyzerService.defaultAnalyzer(), body), Integer.parseInt(file.getName()), null));
            } catch (IOException e) {
                //ignore
            }
        }
        Collections.sort(spamM, Algorithm.comparator);
        int len = spamM.size();

        for (int k = 0; k < thresh_hold; k++) {

            List<FixSizedBloomFilter> temp = new ArrayList(len);
            for (int j = 0; j < len; j++) {
                temp.add(new FixSizedBloomFilter(Algorithm.substituteBits(spamM.get(j).bitSet, k, thresh_hold), j));
            }
            prefix_temp.put(k, temp);

        }
    }

    public boolean isSpam(String content) {

        if (!enable) return false;
        double wow = ((double) inner_similarity(new FixSizedBloomFilter(Algorithm.chunking((MMSegAnalyzer) analyzerService.defaultAnalyzer(), content), 0, null)).size()) / spamM.size();
        if (wow > 0.5) return true;
        return false;
    }

    private Set<FixSizedBloomFilter> inner_similarity(final FixSizedBloomFilter fixSizedBloomFilter) {


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
                        map_temp_list.add(spamM.get(fixSizedBloomFilter1.doc));
                    }
                }
                System.out.println(map_temp_list.size());
                for (FixSizedBloomFilter really_fsb : map_temp_list) {
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
