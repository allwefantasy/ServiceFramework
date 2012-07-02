package net.csdn.modules.analyzer.mmseg4j.analysis;

import net.csdn.modules.analyzer.mmseg4j.Word;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;

/**
 * 切分“字母和数”混在一起的过虑器。比如：mb991ch 切为 "mb 991 ch"
 *
 * @author chenlb 2009-10-14 下午04:03:18
 */
public class CutLetterDigitFilter extends TokenFilter {

    protected Queue<Token> tokenQueue = new LinkedList<Token>();

    private TermAttribute termAtt;
    private OffsetAttribute offsetAtt;
    private TypeAttribute typeAtt;
    private Token reusableToken;

    public CutLetterDigitFilter(TokenStream input) {
        super(input);

        reusableToken = new Token();
        termAtt = (TermAttribute) addAttribute(TermAttribute.class);
        offsetAtt = (OffsetAttribute) addAttribute(OffsetAttribute.class);
        typeAtt = (TypeAttribute) addAttribute(TypeAttribute.class);
    }

    //兼容 lucene 2.9
    public Token next(Token reusableToken) throws IOException {
        return nextToken(reusableToken);
    }

    private Token nextToken(Token reusableToken) throws IOException {
        assert reusableToken != null;

        //先使用上次留下来的。
        Token nextToken = tokenQueue.poll();
        if (nextToken != null) {
            return nextToken;
        }

        /*//在 TokenUtils.nextToken 已经调用了 inc
          if(!input.incrementToken()) {
              return null;
          }*/

        /*TermAttribute termAtt = (TermAttribute)input.getAttribute(TermAttribute.class);
          OffsetAttribute offsetAtt = (OffsetAttribute)input.getAttribute(OffsetAttribute.class);
          TypeAttribute typeAtt = (TypeAttribute)input.getAttribute(TypeAttribute.class);

          nextToken = reusableToken.reinit(termAtt.termBuffer(), 0, termAtt.termLength(), offsetAtt.startOffset(), offsetAtt.endOffset(), typeAtt.type());*/

        nextToken = TokenUtils.nextToken(input, reusableToken);

        if (nextToken != null &&
                (Word.TYPE_LETTER_OR_DIGIT.equalsIgnoreCase(nextToken.type())
                        || Word.TYPE_DIGIT_OR_LETTER.equalsIgnoreCase(nextToken.type()))
                ) {
            final char[] buffer = nextToken.termBuffer();
            final int length = nextToken.termLength();
            byte lastType = (byte) Character.getType(buffer[0]);    //与上次的字符是否同类
            int termBufferOffset = 0;
            int termBufferLength = 0;
            for (int i = 0; i < length; i++) {
                byte type = (byte) Character.getType(buffer[i]);
                if (type <= Character.MODIFIER_LETTER) {
                    type = Character.LOWERCASE_LETTER;
                }
                if (type != lastType) {    //与上一次的不同
                    addToken(nextToken, termBufferOffset, termBufferLength, lastType);

                    termBufferOffset += termBufferLength;
                    termBufferLength = 0;

                    lastType = type;
                }

                termBufferLength++;
            }
            if (termBufferLength > 0) {    //最后一次
                addToken(nextToken, termBufferOffset, termBufferLength, lastType);
            }
            nextToken = tokenQueue.poll();
        }

        return nextToken;
    }

    private void addToken(Token oriToken, int termBufferOffset, int termBufferLength, byte type) {
        Token token = new Token(oriToken.termBuffer(), termBufferOffset, termBufferLength,
                oriToken.startOffset() + termBufferOffset, oriToken.startOffset() + termBufferOffset + termBufferLength);

        if (type == Character.DECIMAL_DIGIT_NUMBER) {
            token.setType(Word.TYPE_DIGIT);
        } else {
            token.setType(Word.TYPE_LETTER);
        }

        tokenQueue.offer(token);
    }

    public void close() throws IOException {
        super.close();
        tokenQueue.clear();
    }

    public void reset() throws IOException {
        super.reset();
        tokenQueue.clear();
    }

    public boolean incrementToken() throws IOException {
        clearAttributes();
        Token token = nextToken(reusableToken);
        if (token != null) {
            termAtt.setTermBuffer(token.termBuffer(), 0, token.termLength());
            offsetAtt.setOffset(token.startOffset(), token.endOffset());
            typeAtt.setType(token.type());
            return true;
        } else {
            end();
            return false;
        }
    }

    public void end() {
        try {
            reset();
        } catch (IOException e) {
        }
    }
}
