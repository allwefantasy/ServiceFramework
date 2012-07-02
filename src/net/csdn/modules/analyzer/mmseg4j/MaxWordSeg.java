package net.csdn.modules.analyzer.mmseg4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 最多分词. 在ComplexSeg基础上把长的词拆.
 *
 * @author chenlb 2009-4-6 下午08:12:11
 */
public class MaxWordSeg extends ComplexSeg {

    public MaxWordSeg(Dictionary dic) {
        super(dic);
    }

    public Chunk seg(Sentence sen) {

        Chunk chunk = super.seg(sen);
        if (chunk != null) {
            List<Word> cks = new ArrayList<Word>();
            for (int i = 0; i < chunk.getCount(); i++) {
                Word word = chunk.words[i];

                if (word.getLength() < 3) {
                    cks.add(word);
                } else {
                    char[] chs = word.getSen();
                    int offset = word.getWordOffset(), n = 0, wordEnd = word.getWordOffset() + word.getLength();
                    int senStartOffset = word.getStartOffset() - offset;    //sen 在文件中的位置
                    int end = -1;    //上一次找到的位置
                    for (; offset < wordEnd - 1; offset++) {
                        int idx = search(chs, offset, 1);
                        if (idx > -1) {
                            cks.add(new Word(chs, senStartOffset, offset, 2));
                            end = offset + 2;
                            n++;
                        } else if (offset >= end) {    //有单字
                            cks.add(new Word(chs, senStartOffset, offset, 1));
                            end = offset + 1;

                        }
                    }
                    if (end > -1 && end < wordEnd) {
                        cks.add(new Word(chs, senStartOffset, offset, 1));
                    }
                }

            }
            chunk.words = cks.toArray(new Word[cks.size()]);
            chunk.count = cks.size();
        }

        return chunk;
    }

}
