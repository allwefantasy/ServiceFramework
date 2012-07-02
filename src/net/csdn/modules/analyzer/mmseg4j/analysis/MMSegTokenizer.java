package net.csdn.modules.analyzer.mmseg4j.analysis;

import net.csdn.modules.analyzer.mmseg4j.MMSeg;
import net.csdn.modules.analyzer.mmseg4j.Seg;
import net.csdn.modules.analyzer.mmseg4j.Word;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;

import java.io.IOException;
import java.io.Reader;

public class MMSegTokenizer extends Tokenizer {

    private MMSeg mmSeg;

    private CharTermAttribute termAtt;
    private OffsetAttribute offsetAtt;
    private TypeAttribute typeAtt;

    public MMSegTokenizer(Seg seg, Reader input) {
        super(input);
        mmSeg = new MMSeg(input, seg);

        termAtt = (CharTermAttribute) addAttribute(CharTermAttribute.class);
        offsetAtt = (OffsetAttribute) addAttribute(OffsetAttribute.class);
        typeAtt = (TypeAttribute) addAttribute(TypeAttribute.class);
    }

    public void reset(Reader input) throws IOException {
        super.reset(input);
        mmSeg.reset(input);
    }

/*//lucene 2.9 以下
 	public Token next(Token reusableToken) throws IOException {
		Token token = null;
		Word word = mmSeg.next();
		if(word != null) {
			//lucene 2.3
			reusableToken.clear();
			reusableToken.setTermBuffer(word.getSen(), word.getWordOffset(), word.getLength());
			reusableToken.setStartOffset(word.getStartOffset());
			reusableToken.setEndOffset(word.getEndOffset());
			reusableToken.setType(word.getType());
			
			token = reusableToken;
			
			//lucene 2.4
			//token = reusableToken.reinit(word.getSen(), word.getWordOffset(), word.getLength(), word.getStartOffset(), word.getEndOffset(), word.getType());
		}
		
		return token;
	}*/

    //lucene 2.9/3.0
    @Override
    public final boolean incrementToken() throws IOException {
        clearAttributes();
        Word word = mmSeg.next();
        if (word != null) {
            //lucene 3.0
            //termAtt.setTermBuffer(word.getSen(), word.getWordOffset(), word.getLength());
            //lucene 3.1
            termAtt.copyBuffer(word.getSen(), word.getWordOffset(), word.getLength());
            offsetAtt.setOffset(word.getStartOffset(), word.getEndOffset());
            typeAtt.setType(word.getType());
            return true;
        } else {
            end();
            return false;
        }
    }
}
