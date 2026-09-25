package net.csdn.remotenotes.controller;

import net.csdn.annotation.rest.At;
import net.csdn.jpa.model.JPABase;
import net.csdn.jpa.model.JPQL;
import net.csdn.modules.http.RestRequest;
import net.csdn.remotenotes.OrmCalls;
import net.csdn.remotenotes.model.Note;
import net.csdn.remotenotes.model.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static net.csdn.common.collections.WowCollections.map;

public class NoteController extends ApiSupport {
    @At(path = "/notes", types = {RestRequest.Method.POST})
    public void create() {
        try {
            Tag tag = null;
            Integer tagId = intParam("tagId");
            if (tagId != null) {
                tag = (Tag) OrmCalls.findById(Tag.class, tagId);
                if (tag == null) {
                    fail(404, "tag-not-found", "Tag.findById");
                    return;
                }
            }
            Note note = (Note) OrmCalls.create(Note.class, map("title", param("title"), "body", param("body")));
            if (tag != null) {
                note.attr("tag", tag);
            }
            if (!note.save()) {
                List<Map<String, String>> errors = errorsOf(note);
                rollbackNow();
                Map<String, Object> body = new LinkedHashMap<String, Object>();
                body.put("error", "invalid");
                body.put("orm", "Note.create + Note.validate presence + Note.save");
                body.put("errors", errors);
                render(400, body);
                return;
            }
            Map<String, Object> body = noteJson(note);
            commitNow();
            body.put("orm", tag == null
                    ? "Note.create + Note.save"
                    : "Note.create + Note.tag ManyToOne + Note.save");
            render(200, body);
        } catch (RuntimeException ex) {
            throw finish(ex);
        }
    }

    @At(path = "/notes/{id}", types = {RestRequest.Method.GET})
    public void show() {
        try {
            Note note = (Note) OrmCalls.findById(Note.class, intParam("id"));
            if (note == null) {
                fail(404, "not-found", "Note.findById");
                return;
            }
            Map<String, Object> body = noteJson(note);
            body.put("orm", "Note.findById + Note.tag");
            render(200, body);
        } catch (RuntimeException ex) {
            throw finish(ex);
        }
    }

    @At(path = "/notes/{id}", types = {RestRequest.Method.POST})
    public void update() {
        try {
            Note note = (Note) OrmCalls.findById(Note.class, intParam("id"));
            if (note == null) {
                fail(404, "not-found", "Note.findById");
                return;
            }
            if (param("title") != null) {
                note.attr("title", param("title"));
            }
            if (param("body") != null) {
                note.attr("body", param("body"));
            }
            if (!note.update()) {
                List<Map<String, String>> errors = errorsOf(note);
                rollbackNow();
                Map<String, Object> body = new LinkedHashMap<String, Object>();
                body.put("error", "invalid");
                body.put("orm", "Note.update");
                body.put("errors", errors);
                render(400, body);
                return;
            }
            Note loaded = (Note) OrmCalls.findById(Note.class, note.id());
            Map<String, Object> body = noteJson(loaded);
            commitNow();
            body.put("orm", "Note.findById + Note.update + Note.findById");
            render(200, body);
        } catch (RuntimeException ex) {
            throw finish(ex);
        }
    }

    @At(path = "/notes/{id}", types = {RestRequest.Method.DELETE})
    public void remove() {
        try {
            Note note = (Note) OrmCalls.findById(Note.class, intParam("id"));
            if (note == null) {
                fail(404, "not-found", "Note.findById");
                return;
            }
            Integer id = note.id();
            note.delete();
            commitNow();
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("id", id);
            body.put("deleted", Boolean.TRUE);
            body.put("orm", "Note.findById + Note.delete");
            render(200, body);
        } catch (RuntimeException ex) {
            throw finish(ex);
        }
    }

    @At(path = "/notes", types = {RestRequest.Method.GET})
    public void list() {
        try {
            int limit = bounded("limit", 20, 50);
            int offset = bounded("offset", 0, 10000);
            JPQL query = filtered();
            long matched = query.count_fetch();
            List fetched = query.order("id").limit(limit).offset(offset).fetch();
            List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < fetched.size(); i++) {
                items.add(noteJson((JPABase) fetched.get(i)));
            }
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("total", OrmCalls.count(Note.class));
            body.put("matched", matched);
            body.put("items", items);
            body.put("orm", "Note.where + order + limit + offset + count + count_fetch");
            render(200, body);
        } catch (RuntimeException ex) {
            throw finish(ex);
        }
    }

    @At(path = "/tags/{id}/notes", types = {RestRequest.Method.GET})
    public void byAssociation() {
        try {
            Tag tag = (Tag) OrmCalls.findById(Tag.class, intParam("id"));
            if (tag == null) {
                fail(404, "not-found", "Tag.findById");
                return;
            }
            List fetched = tag.notes().fetch();
            List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
            for (int i = 0; i < fetched.size(); i++) {
                items.add(noteJson((JPABase) fetched.get(i)));
            }
            Map<String, Object> body = new LinkedHashMap<String, Object>();
            body.put("tagId", tag.id());
            body.put("matched", tag.notes().count());
            body.put("items", items);
            body.put("orm", "Tag.notes.fetch + Tag.notes.count");
            render(200, body);
        } catch (RuntimeException ex) {
            throw finish(ex);
        }
    }

    private JPQL filtered() {
        Integer tagId = intParam("tagId");
        String title = param("title");
        boolean titled = title != null && title.trim().length() > 0;
        if (tagId != null) {
            Tag tag = (Tag) OrmCalls.findById(Tag.class, tagId);
            if (tag == null) {
                fail(404, "tag-not-found", "Tag.findById");
            }
            JPQL query = OrmCalls.where(Note.class, "tag=:tag", map("tag", tag));
            if (titled) {
                query.where("title=:title", map("title", title.trim()));
            }
            return query;
        }
        if (titled) {
            return OrmCalls.where(Note.class, "title=:title", map("title", title.trim()));
        }
        return OrmCalls.where(Note.class, "id>:id", map("id", 0));
    }
}
