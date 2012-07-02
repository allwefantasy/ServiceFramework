package com.allwefantasy.service.hello;

import com.allwefantasy.service.hello.impl.CoolHelloService;
import net.csdn.annotation.Service;

/**
 * User: WilliamZhu
 * Date: 12-7-1
 * Time: 下午8:24
 */
@Service(implementedBy = CoolHelloService.class)
public interface HelloService {
    public String sayHello();
}
