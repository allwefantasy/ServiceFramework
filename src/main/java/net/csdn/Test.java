package net.csdn;

import net.csdn.bootstrap.Application;

/**
 * 12/25/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class Test {
    public static void main(String[] args) {
        ServiceFramwork.scanService.setLoader(Test.class);
        Application.main(args);
    }
}
