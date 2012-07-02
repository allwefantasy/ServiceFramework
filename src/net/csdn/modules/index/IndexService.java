package net.csdn.modules.index;

import net.csdn.common.path.Url;

import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-6-7
 * Time: 上午10:17
 */
public interface IndexService {
    public boolean bulkIndex(String index, String type, String bulkIndexDataStr, List<Url> urls);

    public IndexService deleteIndex(String index);

    public IndexService flushIndex(String index);

    public IndexService refreshIndex(String index);
}
