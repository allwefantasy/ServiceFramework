package net.csdn.cluster.routing;

import com.google.common.collect.Lists;
import net.csdn.cluster.routing.djb.DjbHashFunction;

import java.io.Serializable;
import java.util.List;

/**
 * User: william
 * Date: 11-9-29
 * Time: 上午10:41
 */
public class Routing implements Serializable {
    private static final long serialVersionUID = 1L;
    private HashFunction hashFunction = new DjbHashFunction();
    private String index;
    private List<Shard> shards = Lists.newArrayList();

    public Shard shard(String uid) {

        int shardNum = shards.size();
        int shardId = Math.abs(hashFunction.hash(uid)) % shardNum;

        return findShard(shardId);
    }


    public List<Shard> shards() {
        return this.shards;
    }

    public Shard findShard(int shardId) {
        for (Shard shard : shards) {
            if (shard.shardId() == shardId) {
                return shard;
            }
        }
        return null;
    }

    public Routing(String index) {
        this.index = index;
    }

    public Routing shard(Shard shard) {
        for (Shard temp : shards) {
            if (temp.toString().equals(shard.toString())) {
                return this;
            }
        }
        shards.add(shard);
        return this;
    }

    public Routing start() {

        return this;
    }
}
