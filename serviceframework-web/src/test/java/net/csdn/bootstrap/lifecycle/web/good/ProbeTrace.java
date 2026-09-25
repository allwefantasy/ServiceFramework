package net.csdn.bootstrap.lifecycle.web.good;

import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Singleton
public class ProbeTrace {
    private final List<String> events = Collections.synchronizedList(new ArrayList<String>());

    public void add(String event) {
        events.add(event);
    }

    public List<String> events() {
        return new ArrayList<String>(events);
    }

    public void clear() {
        events.clear();
    }
}
