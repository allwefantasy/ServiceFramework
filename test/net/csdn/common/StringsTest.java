package net.csdn.common;

import junit.framework.Assert;
import org.junit.Test;

/**
 * User: WilliamZhu
 * Date: 12-10-21
 * Time: 上午11:47
 */
public class StringsTest {
    @Test
    public void extractFieldFromGetSetMethodTest(){
         String fileName = Strings.extractFieldFromGetSetMethod("getName");
        Assert.assertTrue(fileName.equals("name"));
    }
}
