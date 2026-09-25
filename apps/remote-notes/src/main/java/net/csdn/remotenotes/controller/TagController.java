package net.csdn.remotenotes.controller;

import net.csdn.annotation.rest.At;
import net.csdn.jpa.association.Association;
import net.csdn.jpa.model.JPABase;
import net.csdn.jpa.model.JPQL;
import net.csdn.modules.http.RestRequest;
import net.csdn.remotenotes.OrmCalls;
import net.csdn.remotenotes.model.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;

public class TagController extends ApiSupport {
    @At(path = "/tags", types = {RestRequest.Method.POST})
    public void create() {
        try {
            String name = param("name");
            Tag tag = (Tag) OrmCalls.create(Tag.class, map("name", name));
            if (!tag.save()) {
                List<Map<String, String>> errors = errorsOf(tag);
                int status = uniqueness(errors) ? 409 : 400;
                rollbackNow();
                Map<String, Object> body = new LinkedHashMap<String, Object>();
                body.put("error", status == 409 ? "duplicate" : "invalid");
                body.put("orm", "Tag.create + Tag.validate presence/uniqueness + Tag.save");
                body.put("errors", errors);
                render(status, body);
                return;
            }
            commitNow();
            Map<String, Object> body = tagJson(tag, 0L);
            body.put("orm", "Tag.create + Tag.save");
            render(200, body);
        } catch (RuntimeException ex) {
            throw finish(ex);
        }
    }

    @At(path = "/tags/{id}", types = {RestRequest.Method.GET})
    public void show() {
        try {
            Tag tag = (Tag) OrmCalls.findById(Tag.class, intParam("id"));
            if (tag == null) {
                fail(404, "not-found", "Tag.findById");
                return;
            }
            Association notes = tag.notes();
            Map<String, Object> body = tagJson(tag, notes.count());
            body.put("orm", "Tag.findById + Tag.notes.count");
            render(200, body);
        } catch (RuntimeException ex) {
            throw finish(ex);
        }
    }

    @At(path = "/tags", types = {RestRequest.Method.GET})
    public void list() {
        try {
            String name = param("name");
            int limit = bounded("limit", 20, 50);
            int offset = bounded("offset", 0, 10000);
            JPQL query;
            String orm;
            if (name != null && name.trim().length() > 0) {
                query = OrmCalls.where(Tag.class, "name=:name", map("name", name.trim()));
                orm = "Tag.where + order + limit + offset + count_fetch";
            } else {
                query = OrmCalls.where(Tag.class, "id>:id", map("id", 0));
                orm = "Tag.where + order + limit + offset + count_fetch";
            }
            long matched = query.count_fetch();
            List fetched = query.order("id").limit(limit).offset(offset).fetch();
            List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < fetched.size(); i++) {
                JPABase tag = (JPABase) fetched.get(i);
                items.add(tagJson(tag, ((Tag) tag).notes().count()));
            }
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("total", OrmCalls.count(Tag.class));
            body.put("matched", matched);
            body.put("items", items);
            body.put("orm", orm + " + Tag.count + Tag.notes.count");
            render(200, body);
        } catch (RuntimeException ex) {
            throw finish(ex);
        }
    }

    private static boolean uniqueness(List<Map<String, String>> errors) {
        for (int i = 0; i < errors.size(); i++) {
            String message = errors.get(i).get("message");
            if (message != null && message.contains("not uniq")) {
                return true;
            }
        }
        return false;
    }
}
