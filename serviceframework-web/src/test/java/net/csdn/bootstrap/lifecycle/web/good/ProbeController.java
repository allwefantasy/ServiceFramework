package net.csdn.bootstrap.lifecycle.web.good;

import com.google.inject.Inject;
import net.csdn.annotation.rest.At;
import net.csdn.common.collections.WowCollections;
import net.csdn.common.settings.Settings;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.ViewType;
import net.csdn.modules.http.WowAroundFilter;

public class ProbeController extends ApplicationController {
    @Inject
    ProbeTrace trace;
    @Inject
    ProbeService service;
    @Inject
    ProbeUtil util;

    static {
        beforeFilter("markBefore", WowCollections.map("only", WowCollections.list("show", "explode")));
        beforeFilter("markBeforeFail", WowCollections.map("only", WowCollections.list("deny")));
        aroundFilter("markOuter", WowCollections.map());
        aroundFilter("markInner", WowCollections.map());
        afterFilter("markAfter", WowCollections.map());
    }

    public void markBefore() {
        trace.add("before");
    }

    public void markBeforeFail() {
        trace.add("before-fail");
        throw new IllegalStateException("before-failed");
    }

    public void markOuter(WowAroundFilter next) throws Exception {
        trace.add("outer-in");
        next.invoke();
        trace.add("outer-out");
    }

    public void markInner(WowAroundFilter next) throws Exception {
        trace.add("inner-in");
        next.invoke();
        trace.add("inner-out");
    }

    public void markAfter() {
        trace.add("after");
    }

    @At(path = "/probe", types = {RestRequest.Method.GET})
    public void show() {
        trace.add("action");
        render(200, service.value() + ":" + util.value() + ":" + token(), ViewType.string);
    }

    @At(path = "/probe/explode", types = {RestRequest.Method.GET})
    public void explode() {
        trace.add("action");
        throw new IllegalStateException("boom");
    }

    @At(path = "/probe/deny", types = {RestRequest.Method.GET})
    public void deny() {
        trace.add("action");
        render(200, "should-not-run", ViewType.string);
    }

    private String token() {
        Settings current = settings;
        String value = current == null ? "" : current.get("application.token");
        return value == null ? "" : value;
    }
}
