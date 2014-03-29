package com.example.service;

/**
 * 12/25/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class TestServiceImpl implements TestService {
    @Override
    public String say(String wow) {
        return say2(wow);
    }

    @Override
    public String say2(String wow) {
        return wow;
    }
}
