package com.example;

import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.Application;

/**
 * 5/23/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class ExampleApplication {
    public static void main(String[] args) {
        ServiceFramwork.scanService.setLoader(ExampleApplication.class);
        Application.main(args);
    }
}
