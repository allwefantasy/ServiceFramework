package com.example.controller.api.mock;

import com.example.controller.api.TagController;
import net.csdn.annotation.Param;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.transport.HttpTransportService;

import java.util.Map;

/**
 * 7/2/15 WilliamZhu(allwefantasy@gmail.com)
 */
public class TagControllerMock implements TagController {

    @Override
    public HttpTransportService.SResponse sayHello(RestRequest.Method method, Map<String, String> params) {
        throw new RuntimeException("not implemented yet...");
    }

    @Override
    public HttpTransportService.SResponse sayHello2(String json, Map<String, String> params) {
        throw new RuntimeException("not implemented yet...");
    }

    @Override
    public HttpTransportService.SResponse sayHello3(@Param("kitty") String kitty) {
        throw new RuntimeException("not implemented yet...");
    }

}
