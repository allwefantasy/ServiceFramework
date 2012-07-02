package net.csdn.modules.deduplicate;

import com.google.inject.Inject;
import net.csdn.modules.deduplicate.service.BloomFilterService;
import net.csdn.modules.factory.CommonFactory;
import net.csdn.modules.transport.data.SearchHit;

import java.util.List;

/**
 * User: WilliamZhu
 * Date: 12-6-8
 * Time: 下午9:50
 */
public class BloomDuplicateFilterService implements DuplicateFilterService {


    @Inject
    private CommonFactory commonFactory;

    @Override
    public List<SearchHit> filter(List<SearchHit> hits) {
        BloomFilterService bloomFilterService = commonFactory.boomfilterService(hits);
        bloomFilterService.result(hits);
        return hits;
    }
}
