package net.csdn.modules.analyzer.mmseg4j;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Reader 流的分词(有字母,数字等), 析出中文(其实是 CJK)成句子 {@link Sentence} 再对 mmseg 算法分词.<br/>
 * <p/>
 * 非线程安全
 *
 * @author chenlb 2009-9-20下午10:41:41
 */
public class MMSeg {

    private PushbackReader reader;
    private Seg seg;

    private StringBuilder bufSentence = new StringBuilder(256);
    private Sentence currentSentence;
    private Queue<Word> bufWord;    // word 缓存, 因为有 chunk 分析三个以上.

    public MMSeg(Reader input, Seg seg) {
        this.seg = seg;

        reset(input);
    }

    private int readedIdx = 0;

    public void reset(Reader input) {
        this.reader = new PushbackReader(new BufferedReader(input), 20);
        currentSentence = null;
        bufWord = new LinkedList<Word>();
        bufSentence.setLength(0);
        readedIdx = -1;
    }

    private int readNext() throws IOException {
        int d = reader.read();
        if (d > -1) {
            readedIdx++;
            d = Character.toLowerCase(d);
        }
        return d;
    }

    private void pushBack(int data) throws IOException {
        readedIdx--;
        reader.unread(data);
    }


    public Word next() throws IOException {
        //先从缓存中取
        Word word = bufWord.poll();
        ;
        if (word == null) {
            bufSentence.setLength(0);

            int data = -1;
            boolean read = true;
            while (read && (data = readNext()) != -1) {
                read = false;    //默认一次可以读出同一类字符,就可以分词内容
                int type = Character.getType(data);
                String wordType = Word.TYPE_WORD;
                switch (type) {
                    case Character.UPPERCASE_LETTER:
                    case Character.LOWERCASE_LETTER:
                    case Character.TITLECASE_LETTER:
                    case Character.MODIFIER_LETTER:
                        /*
                           * 1. 0x410-0x44f -> А-я	//俄文
                           * 2. 0x391-0x3a9 -> Α-Ω	//希腊大写
                           * 3. 0x3b1-0x3c9 -> α-ω	//希腊小写
                           */
                        data = toAscii(data);
                        NationLetter nl = getNation(data);
                        if (nl == NationLetter.UNKNOW) {
                            read = true;
                            break;
                        }
                        wordType = Word.TYPE_LETTER;
                        bufSentence.appendCodePoint(data);
                        switch (nl) {
                            case EN:
                                //字母后面的数字,如: VH049PA
                                ReadCharByAsciiOrDigitOrSpecialChar rcad = new ReadCharByAsciiOrDigitOrSpecialChar(this.seg.dic);
                                readChars(bufSentence, rcad);
                                if (rcad.hasDigit()) {
                                    wordType = Word.TYPE_LETTER_OR_DIGIT;
                                }
                                //only english
                                //readChars(bufSentence, new ReadCharByAscii());
                                break;
                            case RA:
                                readChars(bufSentence, new ReadCharByRussia());
                                break;
                            case GE:
                                readChars(bufSentence, new ReadCharByGreece());
                                break;
                        }
                        bufWord.add(createWord(bufSentence, wordType));

                        bufSentence.setLength(0);

                        break;
                    case Character.OTHER_LETTER:
                        /*
                           * 1. 0x3041-0x30f6 -> ぁ-ヶ	//日文(平|片)假名
                           * 2. 0x3105-0x3129 -> ㄅ-ㄩ	//注意符号
                           */
                        bufSentence.appendCodePoint(data);
                        readChars(bufSentence, new ReadCharByType(Character.OTHER_LETTER));

                        currentSentence = createSentence(bufSentence);

                        bufSentence.setLength(0);

                        break;
                    case Character.DECIMAL_DIGIT_NUMBER:
                        bufSentence.appendCodePoint(toAscii(data));
                        readChars(bufSentence, new ReadCharDigit());    //读后面的数字, AsciiLetterOr
                        wordType = Word.TYPE_DIGIT;
                        int d = readNext();
                        if (d > -1) {
                            if (seg.isUnit(d)) {    //单位,如时间
                                bufWord.add(createWord(bufSentence, startIdx(bufSentence) - 1, Word.TYPE_DIGIT));    //先把数字添加(独立)

                                bufSentence.setLength(0);

                                bufSentence.appendCodePoint(d);
                                wordType = Word.TYPE_WORD;    //单位是 word
                            } else {    //后面可能是字母和数字
                                pushBack(d);
                                if (readChars(bufSentence, new ReadCharByAsciiOrDigit()) > 0) {    //如果有字母或数字都会连在一起.
                                    wordType = Word.TYPE_DIGIT_OR_LETTER;
                                }
                            }
                        }

                        bufWord.add(createWord(bufSentence, wordType));


                        bufSentence.setLength(0);    //缓存的字符清除

                        break;
                    case Character.LETTER_NUMBER:
                        // ⅠⅡⅢ 单分
                        bufSentence.appendCodePoint(data);
                        readChars(bufSentence, new ReadCharByType(Character.LETTER_NUMBER));

                        int startIdx = startIdx(bufSentence);
                        for (int i = 0; i < bufSentence.length(); i++) {
                            bufWord.add(new Word(new char[]{bufSentence.charAt(i)}, startIdx++, Word.TYPE_LETTER_NUMBER));
                        }

                        bufSentence.setLength(0);    //缓存的字符清除

                        break;
                    case Character.OTHER_NUMBER:
                        //①⑩㈠㈩⒈⒑⒒⒛⑴⑽⑾⒇ 连着用
                        bufSentence.appendCodePoint(data);
                        readChars(bufSentence, new ReadCharByType(Character.OTHER_NUMBER));

                        bufWord.add(createWord(bufSentence, Word.TYPE_OTHER_NUMBER));
                        bufSentence.setLength(0);
                        break;
                    default:
                        //其它认为无效字符
                        read = true;
                }//switch
            }

            // 中文分词
            if (currentSentence != null) {
                do {
                    Chunk chunk = seg.seg(currentSentence);
                    for (int i = 0; i < chunk.getCount(); i++) {
                        bufWord.add(chunk.getWords()[i]);
                    }
                } while (!currentSentence.isFinish());

                currentSentence = null;
            }

            word = bufWord.poll();
        }

        return word;
    }


    /**
     * 读取下一串指定类型字符.
     *
     * @author chenlb 2009-8-15下午09:09:50
     */
    private static abstract class ReadChar {
        /**
         * 这个字符是否读取, 不读取也不会读下一个字符.
         */
        abstract boolean isRead(int codePoint);

        int transform(int codePoint) {
            return codePoint;
        }
    }

    private static class ReadCharByAsciiOrDigitOrSpecialChar extends ReadChar {

        private boolean hasDigitOrSpecialChar = false;

        public ReadCharByAsciiOrDigitOrSpecialChar(Dictionary dic) {
            special_chars = dic.permitChars();
        }

        private List<Character> special_chars;

        boolean isRead(int codePoint) {
            boolean isRead = Character.isDigit(codePoint) || is_special(codePoint);
            hasDigitOrSpecialChar |= isRead;
            return isAsciiLetter(codePoint) || isRead;
        }

        boolean hasDigit() {
            return hasDigitOrSpecialChar;
        }

        private boolean is_special(int codePoint) {
            for (Character character : special_chars) {
                if ((int) character == codePoint) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * 读取下一串指定类型的字符放到 bufSentence 中.
     *
     * @param bufSentence
     * @param readChar    判断字符的细节.
     * @return 返回读取的个数
     * @throws IOException {@link #readNext()} 或 {@link #pushBack()} 抛出的.
     */
    private int readChars(StringBuilder bufSentence, ReadChar readChar) throws IOException {
        int num = 0;
        int data = -1;
        while ((data = readNext()) != -1) {
            int d = readChar.transform(data);
            if (readChar.isRead(d)) {
                bufSentence.appendCodePoint(d);
                num++;
            } else {    //不是数字回压,要下一步操作
                pushBack(data);
                break;
            }
        }
        return num;
    }

    /**
     * 读取数字
     */
    private static class ReadCharDigit extends ReadChar {

        boolean isRead(int codePoint) {
            int type = Character.getType(codePoint);
            return isDigit(type);
        }

        int transform(int codePoint) {
            return toAscii(codePoint);
        }

    }

    /**
     * 读取字母或数字
     */
    private static class ReadCharByAsciiOrDigit extends ReadCharDigit {

        private boolean hasDigit = false;

        boolean isRead(int codePoint) {
            boolean isRead = super.isRead(codePoint);
            hasDigit |= isRead;
            return isAsciiLetter(codePoint) || isRead;
        }

        boolean hasDigit() {
            return hasDigit;
        }
    }

    /**
     * 读取字母
     */
    @SuppressWarnings("unused")
    private static class ReadCharByAscii extends ReadCharDigit {
        boolean isRead(int codePoint) {
            return isAsciiLetter(codePoint);
        }
    }

    /**
     * 读取俄语
     */
    private static class ReadCharByRussia extends ReadCharDigit {

        boolean isRead(int codePoint) {
            return isRussiaLetter(codePoint);
        }

    }

    /**
     * 读取希腊
     */
    private static class ReadCharByGreece extends ReadCharDigit {

        boolean isRead(int codePoint) {
            return isGreeceLetter(codePoint);
        }

    }

    /**
     * 读取指定类型的字符
     */
    private static class ReadCharByType extends ReadChar {
        int charType;

        public ReadCharByType(int charType) {
            this.charType = charType;
        }

        boolean isRead(int codePoint) {
            int type = Character.getType(codePoint);
            return type == charType;
        }

    }

    private Word createWord(StringBuilder bufSentence, String type) {
        return new Word(toChars(bufSentence), startIdx(bufSentence), type);
    }

    private Word createWord(StringBuilder bufSentence, int startIdx, String type) {
        return new Word(toChars(bufSentence), startIdx, type);
    }

    private Sentence createSentence(StringBuilder bufSentence) {
        return new Sentence(toChars(bufSentence), startIdx(bufSentence));
    }

    /**
     * 取得 bufSentence 的第一个字符在整个文本中的位置
     */
    private int startIdx(StringBuilder bufSentence) {
        return readedIdx - bufSentence.length() + 1;
    }

    /**
     * 从 StringBuilder 里复制出 char[]
     */
    private static char[] toChars(StringBuilder bufSentence) {
        char[] chs = new char[bufSentence.length()];
        bufSentence.getChars(0, bufSentence.length(), chs, 0);
        return chs;
    }

    /**
     * 双角转单角
     */
    private static int toAscii(int codePoint) {
        if ((codePoint >= 65296 && codePoint <= 65305)    //０-９
                || (codePoint >= 65313 && codePoint <= 65338)    //Ａ-Ｚ
                || (codePoint >= 65345 && codePoint <= 65370)    //ａ-ｚ
                ) {
            codePoint -= 65248;
        }
        return codePoint;
    }

    private static boolean isAsciiLetter(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z') || (codePoint >= 'a' && codePoint <= 'z');
    }

    private static boolean isRussiaLetter(int codePoint) {
        return (codePoint >= 'А' && codePoint <= 'я') || codePoint == 'Ё' || codePoint == 'ё';
    }

    private static boolean isGreeceLetter(int codePoint) {
        return (codePoint >= 'Α' && codePoint <= 'Ω') || (codePoint >= 'α' && codePoint <= 'ω');
    }

    /**
     * EN -> 英语
     * RA -> 俄语
     * GE -> 希腊
     */
    private static enum NationLetter {
        EN, RA, GE, UNKNOW
    }

    ;

    private NationLetter getNation(int codePoint) {
        if (isAsciiLetter(codePoint)) {
            return NationLetter.EN;
        }
        if (isRussiaLetter(codePoint)) {
            return NationLetter.RA;
        }
        if (isGreeceLetter(codePoint)) {
            return NationLetter.GE;
        }
        return NationLetter.UNKNOW;
    }

    @SuppressWarnings("unused")
    private static boolean isCJK(int type) {
        return type == Character.OTHER_LETTER;
    }

    private static boolean isDigit(int type) {
        return type == Character.DECIMAL_DIGIT_NUMBER;
    }

    @SuppressWarnings("unused")
    private static boolean isLetter(int type) {
        return type <= Character.MODIFIER_LETTER && type >= Character.UPPERCASE_LETTER;
    }
}
