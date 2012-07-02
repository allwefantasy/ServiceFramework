package net.csdn.modules.analyzer.mmseg4j;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 词典类. 词库目录单例模式.<br/>
 * 保存单字与其频率,还有词库.<br/>
 * 有检测词典变更的接口，外部程序可以使用 {@link #wordsFileIsChange()} 和 {@link #reload()} 来完成检测与加载的工作.
 *
 * @author chenlb 2009-2-20 下午11:34:29
 */
public class Dictionary {

    private static final Logger log = Logger.getLogger(Dictionary.class.getName());
    public static List<String> stopwords = new ArrayList<String>();

    private File dicPath;    //词库目录
    private volatile Map<Character, CharNode> dict;
    private volatile Map<Character, Object> unit;    //单个字的单位
    //特殊字符列表
    private volatile List<Character> permitchars;

    /**
     * 记录 word 文件的最后修改时间
     */
    private Map<File, Long> wordsLastTime = null;
    private long lastLoadTime = 0;

    /**
     * 不要直接使用, 通过 {@link #getDefalutPath()} 使用
     */
    private static File defalutPath = null;
    private static final ConcurrentHashMap<File, Dictionary> dics = new ConcurrentHashMap<File, Dictionary>();

    protected void finalize() throws Throwable {
        /*
           * 使 class reload 的时也可以释放词库
           */
        destroy();
    }

    /**
     * 从默认目录加载词库文件.<p/>
     * 查找默认目录顺序:
     * <ol>
     * <li>从系统属性mmseg.dic.path指定的目录中加载</li>
     * <li>从classpath/data目录</li>
     * <li>从user.dir/data目录</li>
     * </ol>
     *
     * @see #getDefalutPath()
     */
    public static Dictionary getInstance() {
        File path = getDefalutPath();
        return getInstance(path);
    }

    /**
     * @param path 词典的目录
     */
    public static Dictionary getInstance(String path) {
        return getInstance(new File(path));
    }

    /**
     * @param path 词典的目录
     */
    public static Dictionary getInstance(File path) {
        Dictionary dic = dics.get(path);
        if (dic == null) {
            dic = new Dictionary(path);
            dics.put(path, dic);
        }
        return dic;
    }

    /**
     * 销毁, 释放资源. 此后此对像不再可用.
     */
    void destroy() {
        clear(dicPath);

        dicPath = null;
        dict = null;
        unit = null;
    }

    /**
     * @see Dictionary#clear(File)
     */
    public static Dictionary clear(String path) {
        return clear(new File(path));
    }

    /**
     * 从单例缓存中去除
     *
     * @param path
     * @return 没有返回 null
     */
    public static Dictionary clear(File path) {
        return dics.remove(path);
    }

    /**
     * 词典的目录
     */
    private Dictionary(File path) {
        init(path);
    }

    private void init(File path) {
        dicPath = path;
        wordsLastTime = new HashMap<File, Long>();

        reload();    //加载词典
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /**
     * 只要 wordsXXX.dic的文件
     *
     * @return
     */
    protected File[] listWordsFiles() {
        return dicPath.listFiles(new FilenameFilter() {

            public boolean accept(File dir, String name) {

                return name.startsWith("words") && name.endsWith(".dic");
            }

        });
    }

    //加载允许的特殊字符 比如#,+等
    private List<Character> loadSpecialChar(File wordsPath) throws IOException {
        InputStream charsIn = null;
        File charsFile = new File(wordsPath, "chars.permit");
        charsIn = new FileInputStream(charsFile);
        final List<Character> characterList = new CopyOnWriteArrayList<Character>();
        int lineNum = 0;
        long s = System.currentTimeMillis();
        lineNum = loadSpecial(charsIn, new FileLoading() {
            @Override
            public void row(String line, int n) {
                if (line.length() < 1) {
                    return;
                }
                characterList.add(line.trim().charAt(0));
            }
        });
        log.info("chars loaded time=" + (now() - s) + "ms, line=" + lineNum + ", on file=" + charsFile);
        return characterList;
    }

    private List<String> loadStopwords(File wordsPath) throws IOException {
        InputStream charsIn = null;
        File charsFile = new File(wordsPath, "stopwords.dic");
        charsIn = new FileInputStream(charsFile);
        final List<String> characterList = new CopyOnWriteArrayList<String>();
        int lineNum = 0;
        long s = System.currentTimeMillis();
        lineNum = loadSpecial(charsIn, new FileLoading() {
            @Override
            public void row(String line, int n) {
                if (line == null || line.length() < 1) {
                    return;
                }
                characterList.add(line.trim());
            }
        });
        log.info("stopwords loaded time=" + (now() - s) + "ms, line=" + lineNum + ", on file=" + charsFile);
        return characterList;
    }

    private Map<Character, CharNode> loadDic(File wordsPath) throws IOException {
        InputStream charsIn = null;
        File charsFile = new File(wordsPath, "chars.dic");
        if (charsFile.exists()) {
            charsIn = new FileInputStream(charsFile);
            addLastTime(charsFile);    //chars.dic 也检测是否变更
        } else {    //从 jar 里加载
            charsIn = this.getClass().getResourceAsStream("/data/chars.dic");
            charsFile = new File(this.getClass().getResource("/data/chars.dic").getFile());    //only for log
        }
        final Map<Character, CharNode> dic = new HashMap<Character, CharNode>();
        int lineNum = 0;
        long s = now();
        long ss = s;
        lineNum = load(charsIn, new FileLoading() {    //单个字的

            public void row(String line, int n) {
                if (line.length() < 1) {
                    return;
                }
                String[] w = line.split(" ");
                CharNode cn = new CharNode();
                switch (w.length) {
                    case 2:
                        try {
                            cn.setFreq((int) (Math.log(Integer.parseInt(w[1])) * 100));//字频计算出自由度
                        } catch (NumberFormatException e) {
                            //eat...
                        }
                    case 1:

                        dic.put(w[0].charAt(0), cn);
                }
            }
        });
        log.info("chars loaded time=" + (now() - s) + "ms, line=" + lineNum + ", on file=" + charsFile);

        //try load words.dic in jar
        InputStream wordsDicIn = this.getClass().getResourceAsStream("/data/words.dic");
        if (wordsDicIn != null) {
            File wordsDic = new File(this.getClass().getResource("/data/words.dic").getFile());
            loadWord(wordsDicIn, dic, wordsDic);
        }

        File[] words = listWordsFiles();    //只要 wordsXXX.dic的文件
        if (words != null) {    //扩展词库目录
            for (File wordsFile : words) {
                loadWord(new FileInputStream(wordsFile), dic, wordsFile);

                addLastTime(wordsFile);    //用于检测是否修改
            }
        }

        log.info("load all dic use time=" + (now() - ss) + "ms");
        return dic;
    }

    /**
     * @param is        词库文件流
     * @param dic       加载的词保存在结构中
     * @param wordsFile 日志用
     * @throws IOException from {@link #load(InputStream, FileLoading)}
     */
    private void loadWord(InputStream is, Map<Character, CharNode> dic, File wordsFile) throws IOException {
        long s = now();
        int lineNum = load(is, new WordsFileLoading(dic)); //正常的词库
        log.info("words loaded time=" + (now() - s) + "ms, line=" + lineNum + ", on file=" + wordsFile);
    }

    private Map<Character, Object> loadUnit(File path) throws IOException {
        InputStream fin = null;
        File unitFile = new File(path, "units.dic");
        if (unitFile.exists()) {
            fin = new FileInputStream(unitFile);
            addLastTime(unitFile);
        } else {    //在jar包里的/data/unit.dic
            fin = Dictionary.class.getResourceAsStream("/data/units.dic");
            unitFile = new File(Dictionary.class.getResource("/data/units.dic").getFile());
        }

        final Map<Character, Object> unit = new HashMap<Character, Object>();

        long s = now();
        int lineNum = load(fin, new FileLoading() {

            public void row(String line, int n) {
                if (line.length() != 1) {
                    return;
                }
                unit.put(line.charAt(0), Dictionary.class);
            }
        });
        log.info("unit loaded time=" + (now() - s) + "ms, line=" + lineNum + ", on file=" + unitFile);

        return unit;
    }

    /**
     * 加载 wordsXXX.dic 文件类。
     *
     * @author chenlb 2009-10-15 下午02:12:55
     */
    private static class WordsFileLoading implements FileLoading {
        final Map<Character, CharNode> dic;

        /**
         * @param dic 加载的词，保存在此结构中。
         */
        public WordsFileLoading(Map<Character, CharNode> dic) {
            this.dic = dic;
        }

        public void row(String line, int n) {
            if (line.length() < 2) {
                return;
            }
            CharNode cn = dic.get(line.charAt(0));
            if (cn == null) {
                cn = new CharNode();
                dic.put(line.charAt(0), cn);
            }
            cn.addWordTail(tail(line));
        }
    }

    /**
     * 加载词文件的模板
     *
     * @return 文件总行数
     */
    public static int load(InputStream fin, FileLoading loading) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(fin), "UTF-8"));
        String line = null;
        int n = 0;
        while ((line = br.readLine()) != null) {
            if (line == null || line.startsWith("#")) {
                continue;
            }
            n++;
            loading.row(line, n);
        }
        return n;
    }

    public static int loadSpecial(InputStream fin, FileLoading loading) throws IOException {
        BufferedReader br = new BufferedReader(
                new InputStreamReader(new BufferedInputStream(fin), "UTF-8"));
        String line = null;
        int n = 0;
        while ((line = br.readLine()) != null) {
            if (line == null) {
                continue;
            }
            n++;
            loading.row(line, n);
        }
        return n;
    }

    /**
     * 取得 str 除去第一个char的部分
     *
     * @author chenlb 2009-3-3 下午10:05:26
     */
    private static char[] tail(String str) {
        char[] cs = new char[str.length() - 1];
        str.getChars(1, str.length(), cs, 0);
        return cs;
    }

    public static interface FileLoading {
        /**
         * @param line 读出的一行
         * @param n    当前第几行
         * @author chenlb 2009-3-3 下午09:55:54
         */
        void row(String line, int n);
    }

    /**
     * 把 wordsFile 文件的最后更新时间加记录下来.
     *
     * @param wordsFile 非 null
     */
    private synchronized void addLastTime(File wordsFile) {
        if (wordsFile != null) {
            wordsLastTime.put(wordsFile, wordsFile.lastModified());
        }
    }


    public List<Character> permitChars() {
        return permitchars;
    }

    /**
     * 词典文件是否有修改过
     *
     * @return
     */
    public synchronized boolean wordsFileIsChange() {
        //检查是否有修改文件,包括删除的
        for (Entry<File, Long> flt : wordsLastTime.entrySet()) {
            File words = flt.getKey();
            if (!words.canRead()) {    //可能是删除了
                return true;
            }
            if (words.lastModified() > flt.getValue()) {    //更新了文件
                return true;
            }
        }
        //检查是否有新文件
        File[] words = listWordsFiles();
        if (words != null) {
            for (File wordsFile : words) {
                if (!wordsLastTime.containsKey(wordsFile)) {    //有新词典文件
                    return true;
                }
            }
        }
        return false;
    }


    /**
     * 全新加载词库，没有成功加载会回滚。<P/>
     * 注意：重新加载时，务必有两倍的词库树结构的内存，默认词库是 50M/个 左右。否则抛出 OOM。
     *
     * @return 是否成功加载
     */
    public synchronized boolean reload() {
        Map<File, Long> oldWordsLastTime = new HashMap<File, Long>(wordsLastTime);
        Map<Character, CharNode> oldDict = dict;
        Map<Character, Object> oldUnit = unit;

        try {
            wordsLastTime.clear();
            dict = loadDic(dicPath);
            unit = loadUnit(dicPath);
            permitchars = loadSpecialChar(dicPath);
            stopwords = loadStopwords(dicPath);
            lastLoadTime = System.currentTimeMillis();
        } catch (IOException e) {
            //rollback
            wordsLastTime.putAll(oldWordsLastTime);
            dict = oldDict;
            unit = oldUnit;

            if (log.isLoggable(Level.WARNING)) {
                log.log(Level.WARNING, "reload dic error! dic=" + dicPath + ", and rollbacked.", e);
            }

            return false;
        }
        return true;
    }

    /**
     * word 能否在词库里找到
     *
     * @author chenlb 2009-3-3 下午11:10:45
     */
    public boolean match(String word) {
        if (word == null || word.length() < 2) {
            return false;
        }
        CharNode cn = dict.get(word.charAt(0));
        return search(cn, word.toCharArray(), 0, word.length() - 1) >= 0;
    }

    public CharNode head(char ch) {
        return dict.get(ch);
    }

    /**
     * sen[offset] 后 tailLen 长的词是否存在.
     *
     * @author chenlb 2009-4-8 下午11:13:49
     * @see CharNode#indexOf(char[], int, int)
     */
    public int search(CharNode node, char[] sen, int offset, int tailLen) {
        if (node != null) {
            return node.indexOf(sen, offset, tailLen);
        }
        return -1;
    }

    public int maxMatch(char[] sen, int offset) {
        CharNode node = dict.get(sen[offset]);
        return maxMatch(node, sen, offset);
    }

    public int maxMatch(CharNode node, char[] sen, int offset) {
        if (node != null) {
            return node.maxMatch(sen, offset + 1);
        }
        return 0;
    }

    public ArrayList<Integer> maxMatch(CharNode node, ArrayList<Integer> tailLens, char[] sen, int offset) {
        tailLens.clear();
        tailLens.add(0);
        if (node != null) {
            return node.maxMatch(tailLens, sen, offset + 1);
        }
        return tailLens;
    }

    public boolean isUnit(Character ch) {
        return unit.containsKey(ch);
    }

    /**
     * 当 words.dic 是从 jar 里加载时, 可能 defalut 不存在
     */
    public static File getDefalutPath() {
        if (defalutPath == null) {
            String defPath = System.getProperty("mmseg.dic.path");
            log.info("look up in mmseg.dic.path=" + defPath);
            if (defPath == null) {
                URL url = Dictionary.class.getClassLoader().getResource("data");
                if (url != null) {
                    defPath = url.getFile();
                    log.info("look up in classpath=" + defPath);
                } else {
                    defPath = System.getProperty("user.dir") + "/data";
                    log.info("look up in user.dir=" + defPath);
                }

            }

            defalutPath = new File(defPath);
            if (!defalutPath.exists()) {
                log.warning("defalut dic path=" + defalutPath + " not exist");
            }
        }
        return defalutPath;
    }

    /**
     * 仅仅用来观察词库.
     */
    public Map<Character, CharNode> getDict() {
        return dict;
    }

    /**
     * 注意：当 words.dic 是从 jar 里加载时，此时 File 可能是不存在的。
     */
    public File getDicPath() {
        return dicPath;
    }

    /**
     * 最后加载词库的时间
     */
    public long getLastLoadTime() {
        return lastLoadTime;
    }
}
