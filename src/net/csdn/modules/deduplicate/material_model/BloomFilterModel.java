package net.csdn.modules.deduplicate.material_model;

import net.csdn.modules.deduplicate.algorithm.bloomfilter.FixSizedBloomFilter;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * User: william
 * Date: 12-4-13
 * Time: 下午3:37
 */
public class BloomFilterModel {
    private ConcurrentHashMap<String, List<String>> namespace_categories = new ConcurrentHashMap<String, List<String>>();
    private ConcurrentHashMap<String, List<FixSizedBloomFilter>> category_materials = new ConcurrentHashMap<String, List<FixSizedBloomFilter>>();

    public BloomFilterModel(String namespace, List<String> categories) {
        namespace_categories.put(namespace, categories);
    }
}
