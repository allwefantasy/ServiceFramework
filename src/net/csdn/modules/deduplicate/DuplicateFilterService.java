package net.csdn.modules.deduplicate;

import net.csdn.modules.deduplicate.service.BloomFilterService;
import net.csdn.modules.transport.data.SearchHit;

import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-6-8
 * Time: 下午9:49
 */
public interface DuplicateFilterService {
    public List<SearchHit> filter(List<SearchHit> hits);
}
