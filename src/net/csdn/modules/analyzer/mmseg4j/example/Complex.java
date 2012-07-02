package net.csdn.modules.analyzer.mmseg4j.example;

import net.csdn.modules.analyzer.mmseg4j.*;

import java.io.*;

public class Complex {

    protected Dictionary dic;

    public Complex() {
        dic = Dictionary.getInstance();
    }

    protected Seg getSeg() {
        return new ComplexSeg(dic);
    }

    public String segWords(Reader input, String wordSpilt) throws IOException {
        StringBuilder sb = new StringBuilder();
        Seg seg = getSeg();    //取得不同的分词具体算法
        MMSeg mmSeg = new MMSeg(input, seg);
        Word word = null;
        boolean first = true;
        while ((word = mmSeg.next()) != null) {
            if (!first) {
                sb.append(wordSpilt);
            }
            String w = word.getString();
            sb.append(w);
            first = false;

        }
        return sb.toString();
    }

    public String segWords(String txt, String wordSpilt) throws IOException {
        return segWords(new StringReader(txt), wordSpilt);
    }

    private void printlnHelp() {
        System.out.println("\n\t-- 说明: 输入 QUIT 或 EXIT 退出");
        System.out.print("\nmmseg4j-" + this.getClass().getSimpleName().toLowerCase() + ">");
    }

    protected void run(String[] args) throws IOException {
        String txt = "京华时报２００８年1月23日报道 昨天，受一股来自C#的强冷空气影响，c++出现大风降温天气，白天最高气温只有零下7摄氏度，同时伴有6到7级的偏北风。";

        if (args.length > 0) {
            txt = args[0];
        }

        System.out.println(segWords(txt, " | "));
        printlnHelp();
        String inputStr = null;
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while ((inputStr = br.readLine()) != null) {
            if (inputStr.equals("QUIT") || inputStr.equals("EXIT")) {
                System.exit(0);
            } else if ("".equals(inputStr)) {
                printlnHelp();
            } else {
                //System.out.println(inputStr);
                System.out.println(segWords(inputStr, " | "));    //分词
                System.out.print("\nmmseg4j-" + this.getClass().getSimpleName().toLowerCase() + ">");
            }
        }
    }

    public static void main(String[] args) throws IOException {

        new Complex().run(args);
    }

}
