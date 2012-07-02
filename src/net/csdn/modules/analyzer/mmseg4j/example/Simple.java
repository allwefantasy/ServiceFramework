package net.csdn.modules.analyzer.mmseg4j.example;

import net.csdn.modules.analyzer.mmseg4j.Seg;
import net.csdn.modules.analyzer.mmseg4j.SimpleSeg;

import java.io.IOException;

/**
 * @author chenlb 2009-3-14 上午12:38:40
 */
public class Simple extends Complex {

    protected Seg getSeg() {

        return new SimpleSeg(dic);
    }

    public static void main(String[] args) throws IOException {
        new Simple().run(args);
    }

}
