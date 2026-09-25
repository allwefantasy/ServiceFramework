package net.csdn.remotenotes.controller;

import net.csdn.common.exception.RenderFinish;
import net.csdn.jpa.JPA;
import net.csdn.jpa.model.JPABase;
import net.csdn.modules.http.ApplicationController;
import net.csdn.remotenotes.model.Tag;
import net.csdn.validate.ValidateResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class ApiSupport extends ApplicationController {
    protected Map<String, Object> noteJson(JPABase note) {
        Map<String, Object> json = new LinkedHashMap<String, Object>();
        json.put("id", note.id());
        json.put("title", note.attr("title", String.class));
        json.put("body", note.attr("body", String.class));
        Tag tag = note.attr("tag", Tag.class);
        if (tag == null) {
            json.put("tagId", null);
            json.put("tagName", null);
        } else {
            json.put("tagId", tag.id());
            json.put("tagName", tag.attr("name", String.class));
        }
        return json;
    }

    protected Map<String, Object> tagJson(JPABase tag, long noteCount) {
        Map<String, Object> json = new LinkedHashMap<String, Object>();
        json.put("id", tag.id());
        json.put("name", tag.attr("name", String.class));
        json.put("noteCount", noteCount);
        return json;
    }

    protected List<Map<String, String>> errorsOf(JPABase model) {
        List<Map<String, String>> errors = new ArrayList<Map<String, String>>();
        for (int i = 0; i < model.validateResults.size(); i++) {
            ValidateResult result = model.validateResults.get(i);
            Map<String, String> item = new LinkedHashMap<String, String>();
            item.put("field", result.getFieldName());
            item.put("message", result.getMessage());
            errors.add(item);
        }
        return errors;
    }

    protected Integer intParam(String name) {
        String raw = param(name);
        if (raw == null || raw.trim().length() == 0) {
            return null;
        }
        return Integer.valueOf(raw.trim());
    }

    protected int bounded(String name, int fallback, int max) {
        Integer value = intParam(name);
        if (value == null || value.intValue() < 0) {
            return fallback;
        }
        if (value.intValue() > max) {
            return max;
        }
        return value.intValue();
    }

    /**
     * The HTTP response is written before the request filter commits.
     * Writers commit or roll back first so the next request sees the result.
     */
    protected void commitNow() {
        JPA.getJPAConfig().getJPAContext().closeTx(false);
    }

    protected void rollbackNow() {
        JPA.getJPAConfig().getJPAContext().closeTx(true);
    }

    protected void fail(int status, String error, String orm) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("error", error);
        body.put("orm", orm);
        render(status, body);
    }

    protected String text(Throwable thrown) {
        String message = thrown.getMessage();
        if (message == null) {
            return thrown.getClass().getSimpleName();
        }
        String lower = message.toLowerCase();
        if (lower.contains("password") || lower.contains("jdbc:")) {
            return thrown.getClass().getSimpleName();
        }
        if (message.length() > 180) {
            return message.substring(0, 180);
        }
        return message;
    }

    protected RuntimeException finish(Throwable thrown) {
        if (thrown instanceof RenderFinish) {
            return (RenderFinish) thrown;
        }
        if (thrown instanceof RuntimeException) {
            return (RuntimeException) thrown;
        }
        return new IllegalStateException(thrown);
    }
}
