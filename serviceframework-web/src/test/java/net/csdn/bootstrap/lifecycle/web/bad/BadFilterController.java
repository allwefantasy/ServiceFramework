package net.csdn.bootstrap.lifecycle.web.bad;

import net.csdn.annotation.rest.At;
import net.csdn.common.collections.WowCollections;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestRequest;

public class BadFilterController extends ApplicationController {
    static {
        beforeFilter("missingMethod", WowCollections.map());
    }

    @At(path = "/bad", types = {RestRequest.Method.GET})
    public void show() {
    }
}
