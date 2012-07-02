package net.csdn.modules.analyzer;/**
 * User: WilliamZhu
 * Date: 12-5-31
 * Time: 下午1:29
 */

import org.apache.lucene.analysis.Analyzer;

public interface AnalyzerService {
    public Analyzer whitespaceAnalyzer();

    public Analyzer defaultAnalyzer();
}
