package net.csdn.modules.threadpool;/**
 * User: WilliamZhu
 * Date: 12-5-31
 * Time: 上午11:18
 */

import net.csdn.common.unit.TimeValue;

import java.util.concurrent.Executor;

public interface ThreadPoolService {
    public Executor executor(String name);

    public <T> T runWithTimeout(int time, final Run<T> run);


    interface Run<T> {
        T run();
    }

    public static class Names {
        public static final String CACHED = "cached";
        public static final String INDEX = "index";
        public static final String SEARCH = "search";
        public static final String MERGE = "merge";
        public static final String REFRESH = "refresh";
    }
}
