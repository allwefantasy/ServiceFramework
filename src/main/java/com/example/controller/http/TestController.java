package com.example.controller.http;

import com.example.service.TestService;
import com.google.inject.Inject;
import net.csdn.annotation.NoTransaction;
import net.csdn.annotation.rest.At;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.ViewType;

/**
 * 12/25/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class TestController extends ApplicationController {

    @At(path = "/say/hello", types = {RestRequest.Method.GET})
    @NoTransaction
    public void say() {
        String sya = testService.say(param("wow"));
        render(200, sya, ViewType.json);
    }

    @At(path = "/say/hello2", types = {RestRequest.Method.GET})
    @NoTransaction
    public void say2() {
        render(200, "{}", ViewType.json);
    }

    @Inject
    private TestService testService;
}
