package net.csdn.cluster.routing;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import net.csdn.common.settings.Settings;
import net.csdn.modules.factory.CommonFactory;
import net.csdn.modules.gateway.GatewayData;
import net.csdn.modules.threadpool.ThreadPoolService;
import org.apache.lucene.index.Engine;
import org.apache.lucene.index.ExtendedIndexSearcher;

import java.io.Serializable;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;


/**
 * User: william
 * Date: 11-9-29
 * Time: 上午10:43
 */
public class Shard implements Serializable {
    private static final long serialVersionUID = 1L;

    private FutureTask<Engine> engineTask;

    private int shardId;
    private String host;
    private String index;

    private GatewayData gatewayData;
    private Settings settings;
    private CommonFactory commonFactory;
    private ThreadPoolService threadPoolService;

    @Inject
    public Shard(CommonFactory commonFactory, ThreadPoolService threadPoolService, GatewayData gatewayData, Settings settings, @Assisted int shardId, @Assisted("hostAndPort") String host, @Assisted("index") String index) {
        this.commonFactory = commonFactory;
        this.gatewayData = gatewayData;
        this.threadPoolService = threadPoolService;
        this.settings = settings;
        this.shardId = shardId;
        this.host = host;
        this.index = index;
    }

    public Shard() {
    }

    public boolean local() {
        return gatewayData.getLocalHost().hostAndPort().equals(host());
    }

    public synchronized Engine engineFutureTask() {
        if (!gatewayData.isInMasterSlaveModel() && !local()) return null;
        try {
            if (engineTask != null) {
                return engineTask.get();
            } else {
                engineTask = new FutureTask<Engine>(new Callable<Engine>() {
                    @Override
                    public Engine call() throws Exception {
                        return (gatewayData.isInMasterSlaveModel() && !gatewayData.getLocalHost().isMaster()) ? commonFactory.createRsyncIndexEngine(Shard.this) : commonFactory.createIndexEngine(Shard.this);
                    }
                });
                threadPoolService.executor(ThreadPoolService.Names.CACHED).execute(engineTask);
                return engineTask.get();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    public Shard engineFutureTask(FutureTask<Engine> _indexEngine) {
        this.engineTask = _indexEngine;
        return this;
    }

    public int shardId() {
        return shardId;
    }

    public String host() {
        return host;
    }


    public Shard shardId(int shardId) {
        this.shardId = shardId;
        return this;
    }

    public Shard host(String host) {
        this.host = host;
        return this;
    }

    public String index() {
        return index;
    }

    public String toKey() {
        return index + "#" + shardId;
    }

    public String toString() {
        return toKey() + "[" + host + "]";
    }


    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof Shard) {
            Shard otherShard = (Shard) obj;
            if (this.host().equals(otherShard.host()) && this.shardId() == otherShard.shardId() && this.index().equals(otherShard.index)) {
                return true;
            }
            return false;
        } else {
            return false;
        }
    }

    public ExtendedIndexSearcher searcher() {
        if (!local()) return null;
        try {
            engineTask.get().readerSearcherHolder().searcher();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    //for json generate
    public String getIndex() {
        return index;
    }

    public void setIndex(String index) {
        this.index = index;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getShardId() {
        return shardId;
    }

    public void setShardId(int shardId) {
        this.shardId = shardId;
    }


}
