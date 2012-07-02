package net.csdn.modules.factory;

import com.google.inject.assistedinject.Assisted;
import com.google.inject.name.Named;
import net.csdn.cluster.routing.Shard;
import net.csdn.modules.deduplicate.service.BloomFilterService;
import net.csdn.modules.transport.data.SearchHit;
import org.apache.lucene.index.Engine;

import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-6-5
 * Time: 上午10:00
 */
public interface CommonFactory {
    Shard create(int shardId, @Assisted("hostAndPort") String host, @Assisted("index") String index);

    @Named("indexEngine")
    Engine createIndexEngine(Shard _shard);

    @Named("rsyncIndexEngine")
    Engine createRsyncIndexEngine(Shard _shard);

    BloomFilterService boomfilterService(List<SearchHit> hits);

}
