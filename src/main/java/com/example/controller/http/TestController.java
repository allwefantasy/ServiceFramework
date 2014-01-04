package com.example.controller.http;

import com.example.service.TestService;
import com.google.inject.Inject;
import net.csdn.annotation.rest.At;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.ViewType;

/**
 * 12/25/13 WilliamZhu(allwefantasy@gmail.com)
 */
public class TestController extends ApplicationController {

    @At(path = "/say/hello", types = {RestRequest.Method.GET})
    public void say() {
        render(200, testService.say(param("wow")), ViewType.string);
    }

    @Inject
    private TestService testService;
}
