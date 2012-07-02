package com.allwefantasy.service.hello.impl;

import com.allwefantasy.service.hello.HelloService;

/**
 * User: WilliamZhu
 * Date: 12-7-1
 * Time: 下午8:25
 */

public class CoolHelloService implements HelloService {
    @Override
    public String sayHello() {
        return "wow welcome!";
    }
}
