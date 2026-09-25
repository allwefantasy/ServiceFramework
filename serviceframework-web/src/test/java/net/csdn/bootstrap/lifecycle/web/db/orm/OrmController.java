package net.csdn.bootstrap.lifecycle.web.db.orm;

import com.google.inject.Inject;
import net.csdn.annotation.rest.At;
import net.csdn.common.settings.Settings;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.ViewType;

public class OrmController extends ApplicationController {
    @Inject
    private Settings injectedSettings;

    @At(path = "/db/orm", types = {RestRequest.Method.GET})
    public void roundTrip() throws Exception {
        String label = param("q");
        WebRecord record = new WebRecord();
        record.getClass().getMethod("setLabel", String.class).invoke(record, label);
        if (!record.save()) {
            render(500, "save-failed", ViewType.string);
            return;
        }
        Object id = record.getClass().getMethod("getId").invoke(record);
        Object found = WebRecord.class.getMethod("findById", Object.class).invoke(null, id);
        if (found == null) {
            render(500, "missing", ViewType.string);
            return;
        }
        Object loaded = found.getClass().getMethod("getLabel").invoke(found);
        String token = injectedSettings == null ? "" : injectedSettings.get("application.token");
        render(200, loaded + ":" + token, ViewType.string);
    }
}
