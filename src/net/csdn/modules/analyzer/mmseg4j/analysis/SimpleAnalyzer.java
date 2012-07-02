package net.csdn.modules.analyzer.mmseg4j.analysis;

import net.csdn.modules.analyzer.mmseg4j.Dictionary;
import net.csdn.modules.analyzer.mmseg4j.Seg;
import net.csdn.modules.analyzer.mmseg4j.SimpleSeg;

import java.io.File;

/**
 * mmseg 的 simple anlayzer.
 *
 * @author chenlb 2009-3-16 下午10:08:13
 */
public class SimpleAnalyzer extends MMSegAnalyzer {

    public SimpleAnalyzer() {
        super();
    }

    public SimpleAnalyzer(String path) {
        super(path);
    }

    public SimpleAnalyzer(Dictionary dic) {
        super(dic);
    }

    public SimpleAnalyzer(File path) {
        super(path);
    }

    protected Seg newSeg() {
        return new SimpleSeg(dic);
    }
}
