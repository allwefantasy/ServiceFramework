package com.example.service;

import net.csdn.annotation.Service;

/**
 * 12/25/13 WilliamZhu(allwefantasy@gmail.com)
 */
@Service(implementedBy = TestServiceImpl.class)
public interface TestService {

    public String say(String wow);
}
