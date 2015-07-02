package com.example.controller.api;

import net.csdn.annotation.rest.At;
import net.csdn.modules.http.RestRequest;

import java.util.Map;

/**
 * 7/2/15 WilliamZhu(allwefantasy@gmail.com)
 */
public interface TagController {
    @At(path = "/say/hello", types = {RestRequest.Method.GET})
    public String sayHello(Map<String, String> params);
}
