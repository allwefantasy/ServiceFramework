package net.csdn.modules.analyzer.mmseg4j;

/**
 * 句子, 在一大串字符中断出连续中文的文本.
 *
 * @author chenlb 2009-3-3 下午11:56:53
 */
public class Sentence {

    private char[] text;
    private int startOffset;

    private int offset;

    public Sentence() {
        text = new char[0];
    }

    public Sentence(char[] text, int startOffset) {
        reinit(text, startOffset);
    }

    public void reinit(char[] text, int startOffset) {
        this.text = text;
        this.startOffset = startOffset;
        offset = 0;
    }

    public char[] getText() {
        return text;
    }

    /**
     * 句子开始处理的偏移位置
     */
    public int getOffset() {
        return offset;
    }

    /**
     * 句子开始处理的偏移位置
     */
    public void setOffset(int offset) {
        this.offset = offset;
    }

    public void addOffset(int inc) {
        offset += inc;
    }

    /**
     * 句子处理完成
     */
    public boolean isFinish() {
        return offset >= text.length;
    }

    /**
     * 句子在文本中的偏移位置
     */
    public int getStartOffset() {
        return startOffset;
    }

    /**
     * 句子在文本中的偏移位置
     */
    public void setStartOffset(int startOffset) {
        this.startOffset = startOffset;
    }
}
