package com.example.controller.api;

import net.csdn.annotation.Param;
import net.csdn.annotation.rest.At;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.transport.HttpTransportService;

import java.util.Map;

/**
 * 7/2/15 WilliamZhu(allwefantasy@gmail.com)
 */
public interface TagController {
    @At(path = "/say/hello", types = {RestRequest.Method.GET, RestRequest.Method.POST})
    public HttpTransportService.SResponse sayHello(RestRequest.Method method, Map<String, String> params);

    @At(path = "/say/hello", types = {RestRequest.Method.GET})
    public HttpTransportService.SResponse sayHello3(@Param("kitty") String kitty);

    @At(path = "/say/hello2", types = {RestRequest.Method.POST})
    public HttpTransportService.SResponse sayHello2(String json, Map<String, String> params);


}
