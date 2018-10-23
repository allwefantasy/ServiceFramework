package net.csdn;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Created by allwefantasy on 29/6/2017.
 */
public class Test {
    public static void main(String[] args) throws MalformedURLException {
        URL jarUrl = new URL("jar:file:/proj/parser/jar/parser.jar!/test.xml");
        System.out.println(jarUrl.getFile().split("\\.jar!")[1]);
    }
}
