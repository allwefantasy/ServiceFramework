package net.csdn.modules.analyzer;/**
 * User: WilliamZhu
 * Date: 12-5-31
 * Time: 下午1:31
 */

import com.google.inject.Inject;
import net.csdn.common.settings.Settings;
import net.csdn.env.Environment;
import net.csdn.modules.analyzer.mmseg4j.Dictionary;
import net.csdn.modules.analyzer.mmseg4j.analysis.MMSegAnalyzer;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.util.Version;

public class DefaultAnalyzerService implements AnalyzerService {
    private Analyzer whitespaceAnalyzer;
    private Analyzer defaultAnalyzer;

    @Inject
    public DefaultAnalyzerService(Settings settings) {
        this.whitespaceAnalyzer = new WhitespaceAnalyzer(Version.LUCENE_32);
        Dictionary dic = Dictionary.getInstance(new Environment(settings).dictionariesFile());
        this.defaultAnalyzer = new MMSegAnalyzer(dic);
    }

    @Override
    public Analyzer whitespaceAnalyzer() {
        return whitespaceAnalyzer;
    }

    @Override
    public Analyzer defaultAnalyzer() {
        return defaultAnalyzer;
    }
}
