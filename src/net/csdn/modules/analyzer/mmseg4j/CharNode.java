package net.csdn.modules.analyzer.mmseg4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 所有词都记录在第一个字的结点下.
 *
 * @author chenlb 2009-2-20 下午11:30:14
 */
public class CharNode {

    private int freq = -1;    //Degree of Morphemic Freedom of One-Character, 单字才需要
    private int maxLen = 0;    //wordTail的最长

    private KeyTree ktWordTails = new KeyTree();
    private int wordNum = 0;

    public CharNode() {

    }

    public void addWordTail(char[] wordTail) {
        ktWordTails.add(wordTail);
        wordNum++;
        if (wordTail.length > maxLen) {
            maxLen = wordTail.length;
        }
    }

    public int getFreq() {
        return freq;
    }

    public void setFreq(int freq) {
        this.freq = freq;
    }

    public int wordNum() {
        return wordNum;
    }

    /**
     * @param sen     句子, 一串文本.
     * @param offset  词在句子中的位置
     * @param tailLen 词尾的长度, 实际是去掉词的长度.
     * @author chenlb 2009-4-8 下午11:10:30
     */
    public int indexOf(char[] sen, int offset, int tailLen) {
        //return binarySearch(wordTails, sen, offset+1, tailLen, casc);
        return ktWordTails.match(sen, offset + 1, tailLen) ? 1 : -1;
    }

    /**
     * @param sen            句子, 一串文本.
     * @param wordTailOffset 词在句子中的位置, 实际是 offset 后面的开始找.
     * @return 返回词尾长, 没有就是 0
     * @author chenlb 2009-4-10 下午10:45:51
     */
    public int maxMatch(char[] sen, int wordTailOffset) {
        return ktWordTails.maxMatch(sen, wordTailOffset);
    }

    /**
     * @return 至少返回一个包括 0的int
     * @author chenlb 2009-4-12 上午10:01:35
     */
    public ArrayList<Integer> maxMatch(ArrayList<Integer> tailLens, char[] sen, int wordTailOffset) {
        return ktWordTails.maxMatch(tailLens, sen, wordTailOffset);
    }

    public int getMaxLen() {
        return maxLen;
    }

    public void setMaxLen(int maxLen) {
        this.maxLen = maxLen;
    }

    public static class KeyTree {
        TreeNode head = new TreeNode(' ');

        public void add(char[] w) {
            if (w.length < 1) {
                return;
            }
            TreeNode p = head;
            for (int i = 0; i < w.length; i++) {
                TreeNode n = p.subNode(w[i]);
                if (n == null) {
                    n = new TreeNode(w[i]);
                    p.born(w[i], n);
                }
                p = n;
            }
            p.alsoLeaf = true;
        }

        /**
         * @return 返回匹配最长词的长度, 没有找到返回 0.
         */
        public int maxMatch(char[] sen, int offset) {
            int idx = offset - 1;
            TreeNode node = head;
            for (int i = offset; i < sen.length; i++) {
                node = node.subNode(sen[i]);
                if (node != null) {
                    if (node.isAlsoLeaf()) {
                        idx = i;
                    }
                } else {
                    break;
                }
            }
            return idx - offset + 1;
        }

        public ArrayList<Integer> maxMatch(ArrayList<Integer> tailLens, char[] sen, int offset) {
            TreeNode node = head;
            for (int i = offset; i < sen.length; i++) {
                node = node.subNode(sen[i]);
                if (node != null) {
                    if (node.isAlsoLeaf()) {
                        tailLens.add(i - offset + 1);
                    }
                } else {
                    break;
                }
            }
            return tailLens;
        }

        public boolean match(char[] sen, int offset, int len) {
            TreeNode node = head;
            for (int i = 0; i < len; i++) {
                node = node.subNode(sen[offset + i]);
                if (node == null) {
                    return false;
                }
            }
            return node.isAlsoLeaf();
        }
    }

    private static class TreeNode {
        char key;
        Map<Character, TreeNode> subNodes;
        boolean alsoLeaf;

        public TreeNode(char key) {
            this.key = key;
            subNodes = new HashMap<Character, TreeNode>();
        }

        public void born(char k, TreeNode sub) {
            subNodes.put(k, sub);
        }

        public TreeNode subNode(char k) {
            return subNodes.get(k);
        }

        public boolean isAlsoLeaf() {
            return alsoLeaf;
        }
    }
}
