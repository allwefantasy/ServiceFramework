package net.csdn.modules.transport.proxy;

import net.csdn.annotation.Param;
import net.csdn.annotation.rest.At;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.transport.HttpTransportService;

import java.util.List;
import java.util.Map;

public interface TestRestApi {

    @At(types = {RestRequest.Method.GET}, path = {"items/{id}"})
    HttpTransportService.SResponse fetch(@Param("id") String id,
                                         scala.collection.immutable.Map<String, String> scalaParams,
                                         Map<String, String> javaParams);

    @At(types = {RestRequest.Method.POST, RestRequest.Method.PUT}, path = {"items/{id}"})
    HttpTransportService.SResponse save(@Param("id") String id,
                                        Map<String, String> params,
                                        String body,
                                        RestRequest.Method method);

    @At(types = {RestRequest.Method.GET}, path = {"ping"})
    List<HttpTransportService.SResponse> ping();
}
