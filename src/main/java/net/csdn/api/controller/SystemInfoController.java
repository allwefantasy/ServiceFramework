package net.csdn.api.controller;

import com.google.inject.Inject;
import net.csdn.annotation.rest.At;
import net.csdn.common.collections.WowCollections;
import net.csdn.modules.controller.API;
import net.csdn.modules.controller.APIDesc;
import net.csdn.modules.controller.QpsManager;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestRequest;

import java.util.Collection;

/**
 * 7/17/14 WilliamZhu(allwefantasy@gmail.com)
 */
public class SystemInfoController extends ApplicationController {

    @At(path = "/81269a3e47b589ac26ad065d571006ad44dbdf2c/service/monitor", types = {RestRequest.Method.GET, RestRequest.Method.POST})
    public void systemInfo() {
        if (!api.enable()) {
            render(map("ok", false, "message", "该服务没有启用。可通过设置application.api.qps.enable 进行设置"));
        }

        Collection<APIDesc> apiDescs = api.collectAPIInfoes().values();
        for (APIDesc apiDesc : apiDescs) {
            apiDesc.setQps(apiDesc.getQps() * 1000 / settings.getAsInt("application.api.qps.internal", 1000));
        }
        render(WowCollections.map("systemStartTime", api.systemStartTime(), "api", apiDescs));
    }

    @At(path = "/81269a3e47b589ac26ad065d571006ad44dbdf2c/service/qps/limit", types = {RestRequest.Method.POST})
    public void qpsLimit() {
        if (!api.enable()) {
            render(map("ok", false, "message", "该服务没有启用。可通过设置application.api.qps.enable 进行设置"));
        }
        qpsManager.configureQpsLimiter(params());
        render(200, qpsManager.qpsConfs());
    }


    @Inject
    private API api;

    @Inject
    private QpsManager qpsManager;

}
