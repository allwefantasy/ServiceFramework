package net.csdn.remotenotes.controller;

import net.csdn.annotation.rest.At;
import net.csdn.jpa.model.Model;
import net.csdn.modules.http.RestRequest;
import net.csdn.remotenotes.AppVersion;
import net.csdn.remotenotes.FrameworkBuild;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HealthController extends ApiSupport {
    @At(path = "/health", types = {RestRequest.Method.GET})
    public void health() {
        try {
            List<Map> rows = Model.findBySql(
                    "SELECT DATABASE() AS db_name, CURRENT_USER() AS db_user, @@version AS db_version, @@hostname AS db_host");
            Map row = rows.isEmpty() ? new LinkedHashMap() : rows.get(0);
            Map<String, Object> mysql = new LinkedHashMap<String, Object>();
            mysql.put("database", cell(row, "db_name"));
            mysql.put("user", cell(row, "db_user"));
            mysql.put("version", cell(row, "db_version"));
            mysql.put("host", cell(row, "db_host"));
            mysql.put("orm", "Model.findBySql");

            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("service", "remote-notes");
            body.put("appVersion", AppVersion.VALUE);
            body.put("marker", AppVersion.MARKER);
            body.put("releaseId", FrameworkBuild.releaseId());
            body.put("framework", FrameworkBuild.identity());
            body.put("mysql", mysql);
            render(200, body);
        } catch (RuntimeException ex) {
            throw finish(ex);
        }
    }

    private static String cell(Map row, String name) {
        if (row == null) {
            return "";
        }
        Object value = row.get(name);
        if (value == null) {
            value = row.get(name.toUpperCase());
        }
        return value == null ? "" : String.valueOf(value);
    }
}
