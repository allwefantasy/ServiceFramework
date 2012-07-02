package net.csdn.modules.threadpool;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import net.csdn.CsdnSearchIllegalArgumentException;
import net.csdn.common.settings.Settings;
import net.csdn.common.unit.TimeValue;

import java.util.Map;
import java.util.concurrent.*;

/**
 * User: william
 * Date: 11-9-5
 * Time: 下午4:49
 */
public class DefaultThreadPoolService implements ThreadPoolService {

    private Settings settings;


    @Inject
    public DefaultThreadPoolService(Settings settings) {
        this.settings = settings;
        Map<String, Executor> executors = Maps.newHashMap();
        executors.put(Names.SEARCH, build(Names.SEARCH));
        executors.put(Names.REFRESH, build(Names.REFRESH));
        executors.put(Names.CACHED, buildCache(Names.CACHED));
        this.executors = ImmutableMap.copyOf(executors);
        this.scheduler = Executors.newScheduledThreadPool(1, Executors.defaultThreadFactory());
    }


    public <T> T runWithTimeout(int timeValue, final Run<T> run) {
        FutureTask<T> futureTask = new FutureTask(new Callable<T>() {
            @Override
            public T call() throws Exception {
                return run.run();
            }
        });
        executor(Names.CACHED).execute(futureTask);
        try {
            return futureTask.get(timeValue, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            //ingore
        }
        return null;
    }


    public DefaultThreadPoolService start() {
//       scheduler.scheduleWithFixedDelay(new Runnable() {
//           @Override
//           public void run() {
//               Map<String,IndexEngine> maps = Cache.cache(Map.class,Cache.Names.INDEX_WRITER);
//               if(maps.size()>0){
//                   for(Map.Entry<String,IndexEngine> entry:maps.entrySet()){
//                       IndexEngine engine = entry.getValue();
//                       engine.refresh();
//                   }
//               }
//           }
//       },settings.getAsInt("index.setting.refresh_delay",10),settings.getAsInt("index.setting.refresh_interval",5),TimeUnit.SECONDS);


        return this;
    }

    public Executor executor(String name) {
        Executor executor = executors.get(name);
        if (executor == null) {
            throw new CsdnSearchIllegalArgumentException("No executor found for [" + name + "]");
        }
        return executor;
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, TimeValue interval) {
        return scheduler.scheduleWithFixedDelay(command, interval.millis(), interval.millis(), TimeUnit.MILLISECONDS);
    }


    public void shutdown() {
        scheduler.shutdown();
        for (Executor executor : executors.values()) {
            if (executor instanceof ThreadPoolExecutor) {
                ((ThreadPoolExecutor) executor).shutdown();
            }
        }
    }

    public void shutdownNow() {
        scheduler.shutdownNow();
        for (Executor executor : executors.values()) {
            if (executor instanceof ThreadPoolExecutor) {
                ((ThreadPoolExecutor) executor).shutdownNow();
            }
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        boolean result = scheduler.awaitTermination(timeout, unit);
        for (Executor executor : executors.values()) {
            if (executor instanceof ThreadPoolExecutor) {
                result &= ((ThreadPoolExecutor) executor).awaitTermination(timeout, unit);
            }
        }
        return result;
    }

    public Executor build(String name) {
        return new ThreadPoolExecutor(0, Integer.MAX_VALUE, 5, TimeUnit.MINUTES, new SynchronousQueue<Runnable>(), Executors.defaultThreadFactory());
    }

    public Executor buildCache(String name) {
        return new ThreadPoolExecutor(5, 1000, 1, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), Executors.defaultThreadFactory());
    }

    private final ImmutableMap<String, Executor> executors;

    private final ScheduledExecutorService scheduler;

}
