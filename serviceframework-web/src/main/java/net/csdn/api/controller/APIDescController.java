package net.csdn.api.controller;


import net.csdn.annotation.rest.At;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestRequest;


public class APIDescController extends ApplicationController {
    @At(path = "/openapi/ui/spec/", types = {RestRequest.Method.GET, RestRequest.Method.POST})
    public void ui() {
        render(200, APIDescAC.openAPIs(settings));
    }
}
