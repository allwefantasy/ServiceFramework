package net.csdn.modules.analyzer.mmseg4j.analysis;

import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.IOException;

/**
 * lucene 3.0 从 TokenStream 得到 Token 比较麻烦。
 *
 * @author chenlb 2010-10-7下午10:07:10
 */
public class TokenUtils {

    /**
     * @param input
     * @param reusableToken is null well new one auto.
     * @return null - if not next token or input is null.
     * @throws IOException
     */
    public static Token nextToken(TokenStream input, Token reusableToken) throws IOException {
        if (input == null) {
            return null;
        }
        if (!input.incrementToken()) {
            return null;
        }

        CharTermAttribute termAtt = (CharTermAttribute) input.getAttribute(CharTermAttribute.class);
        OffsetAttribute offsetAtt = (OffsetAttribute) input.getAttribute(OffsetAttribute.class);
        TypeAttribute typeAtt = (TypeAttribute) input.getAttribute(TypeAttribute.class);

        if (reusableToken == null) {
            reusableToken = new Token();
        }

        reusableToken.clear();
        if (termAtt != null) {
            //lucene 3.0
            //reusableToken.setTermBuffer(termAtt.termBuffer(), 0, termAtt.termLength());
            //lucene 3.1
            reusableToken.copyBuffer(termAtt.buffer(), 0, termAtt.length());
        }
        if (offsetAtt != null) {
            reusableToken.setStartOffset(offsetAtt.startOffset());
            reusableToken.setEndOffset(offsetAtt.endOffset());
        }

        if (typeAtt != null) {
            reusableToken.setType(typeAtt.type());
        }

        return reusableToken;
    }
}
