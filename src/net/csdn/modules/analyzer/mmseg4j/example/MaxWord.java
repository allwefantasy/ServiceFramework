package net.csdn.modules.analyzer.mmseg4j.example;

import net.csdn.modules.analyzer.mmseg4j.MaxWordSeg;
import net.csdn.modules.analyzer.mmseg4j.Seg;

import java.io.IOException;

public class MaxWord extends Complex {

    protected Seg getSeg() {

        return new MaxWordSeg(dic);
    }

    public static void main(String[] args) throws IOException {
        new MaxWord().run(args);
    }
}
