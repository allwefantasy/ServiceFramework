package net.csdn.bootstrap.lifecycle.web.db.mongo;

import com.google.inject.Inject;
import net.csdn.annotation.rest.At;
import net.csdn.common.settings.Settings;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.ViewType;

public class MongoController extends ApplicationController {
    @Inject
    private Settings injectedSettings;

    @At(path = "/db/mongo", types = {RestRequest.Method.GET})
    public void roundTrip() throws Exception {
        String text = param("q");
        WebNote note = new WebNote();
        note.id(text);
        note.setText(text);
        if (!note.insert()) {
            render(500, "insert-failed", ViewType.string);
            return;
        }
        Object found = WebNote.class.getMethod("findById", Object.class).invoke(null, text);
        if (found == null) {
            render(500, "missing", ViewType.string);
            return;
        }
        String loaded = ((WebNote) found).getText();
        String token = injectedSettings == null ? "" : injectedSettings.get("application.token");
        render(200, loaded + ":" + token, ViewType.string);
    }
}
