package com.example.thrift.demo;

import org.apache.thrift.TException;

/**
 * 5/23/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class HelloWordServiceImpl implements HelloWorldService.Iface {
    @Override
    public String sayHello(String username) throws TException {
        return "i am cool," + username;
    }
}
