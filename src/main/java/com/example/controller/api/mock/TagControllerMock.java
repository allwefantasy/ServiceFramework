package com.example.controller.api.mock;

import com.example.controller.api.TagController;

import java.util.Map;

/**
 * 7/2/15 WilliamZhu(allwefantasy@gmail.com)
 */
public class TagControllerMock implements TagController {
    @Override
    public String sayHello(Map<String, String> params) {
        throw new RuntimeException("not implemented yet...");
    }
}
