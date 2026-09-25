package net.csdn.bootstrap.lifecycle.web.db.both;

import com.google.inject.Inject;
import net.csdn.annotation.rest.At;
import net.csdn.bootstrap.lifecycle.web.db.mongo.WebNote;
import net.csdn.bootstrap.lifecycle.web.db.orm.WebRecord;
import net.csdn.common.settings.Settings;
import net.csdn.modules.http.ApplicationController;
import net.csdn.modules.http.RestRequest;
import net.csdn.modules.http.ViewType;

public class BothController extends ApplicationController {
    @Inject
    private Settings injectedSettings;

    @At(path = "/db/both", types = {RestRequest.Method.GET})
    public void roundTrip() throws Exception {
        String value = param("q");
        WebRecord record = new WebRecord();
        record.getClass().getMethod("setLabel", String.class).invoke(record, value);
        if (!record.save()) {
            render(500, "orm-save-failed", ViewType.string);
            return;
        }
        Object id = record.getClass().getMethod("getId").invoke(record);
        Object found = WebRecord.class.getMethod("findById", Object.class).invoke(null, id);
        if (found == null) {
            render(500, "orm-missing", ViewType.string);
            return;
        }
        String label = String.valueOf(found.getClass().getMethod("getLabel").invoke(found));
        WebNote note = new WebNote();
        note.id(value);
        note.setText(value);
        if (!note.insert()) {
            render(500, "mongo-insert-failed", ViewType.string);
            return;
        }
        Object loadedNote = WebNote.class.getMethod("findById", Object.class).invoke(null, value);
        if (loadedNote == null) {
            render(500, "mongo-missing", ViewType.string);
            return;
        }
        String text = ((WebNote) loadedNote).getText();
        String token = injectedSettings == null ? "" : injectedSettings.get("application.token");
        render(200, label + ":" + text + ":" + token, ViewType.string);
    }
}
