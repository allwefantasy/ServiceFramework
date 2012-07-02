package net.csdn.modules.analyzer.mmseg4j.analysis;

import net.csdn.modules.analyzer.mmseg4j.ComplexSeg;
import net.csdn.modules.analyzer.mmseg4j.Dictionary;
import net.csdn.modules.analyzer.mmseg4j.Seg;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

/**
 * 默认使用 max-word
 *
 * @author chenlb
 * @see {@link SimpleAnalyzer}, {@link ComplexAnalyzer}, {@link MaxWordAnalyzer}
 */
public class MMSegAnalyzer extends Analyzer {

    protected Dictionary dic;

    /**
     * @see Dictionary#getInstance()
     */
    public MMSegAnalyzer() {
        dic = Dictionary.getInstance();
    }

    /**
     * @param path 词库路径
     * @see Dictionary#getInstance(String)
     */
    public MMSegAnalyzer(String path) {
        dic = Dictionary.getInstance(path);
    }

    /**
     * @param path 词库目录
     * @see Dictionary#getInstance(File)
     */
    public MMSegAnalyzer(File path) {
        dic = Dictionary.getInstance(path);
    }

    public MMSegAnalyzer(Dictionary dic) {
        super();
        this.dic = dic;
    }

    protected Seg newSeg() {
        return new ComplexSeg(dic);
    }

    public Dictionary getDict() {
        return dic;
    }

    @Override
    public final TokenStream reusableTokenStream(String fieldName, Reader reader)
            throws IOException {

        MMSegTokenizer mmsegTokenizer = (MMSegTokenizer) getPreviousTokenStream();
        if (mmsegTokenizer == null) {
            mmsegTokenizer = new MMSegTokenizer(newSeg(), reader);
            setPreviousTokenStream(mmsegTokenizer);    //保存实例
        } else {
            mmsegTokenizer.reset(reader);
        }

        return mmsegTokenizer;
    }

    public String toWhiteSpaceString(String text) {
        TokenStream stream = null;
        StringBuffer sb = new StringBuffer();
        try {
            stream = reusableTokenStream("contents", new StringReader(text));
            stream.reset();
            CharTermAttribute term = stream.addAttribute(CharTermAttribute.class);
            while (stream.incrementToken()) {
                if (Dictionary.stopwords.contains(term.toString()))
                    continue;
                sb.append(term.toString() + " ");
            }
            stream.end();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (stream != null) try {
                stream.close();
            } catch (IOException e) {
                //ignore
            }
        }
        return sb.toString();
    }

    @Override
    public final TokenStream tokenStream(String fieldName, Reader reader) {
        TokenStream ts = new MMSegTokenizer(newSeg(), reader);
        return ts;
    }
}
