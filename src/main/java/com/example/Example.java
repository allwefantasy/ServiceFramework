package com.example;

import net.csdn.ServiceFramwork;
import net.csdn.bootstrap.Application;

/**
 * 4/25/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class Example extends Application {
    public static void main(String[] args){
        ServiceFramwork.scanService.setLoader(Example.class);
        Application.main(args);
    }
}
