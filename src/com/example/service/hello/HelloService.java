package com.example.service.hello;

import com.example.service.hello.impl.CoolHelloService;
import net.csdn.annotation.Service;

/**
 * BlogInfo: WilliamZhu
 * Date: 12-7-1
 * Time: 下午8:24
 */
@Service(implementedBy = CoolHelloService.class)
public interface HelloService {
    public String sayHello();
}
