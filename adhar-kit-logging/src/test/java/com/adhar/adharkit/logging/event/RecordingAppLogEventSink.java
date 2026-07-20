package com.adhar.adharkit.logging.event;

import java.util.ArrayList;
import java.util.List;

/**
 * Test sink that records every published event for assertions.
 */
public class RecordingAppLogEventSink implements AppLogEventSink {

    private final List<AppLogEvent> events = new ArrayList<>();

    @Override
    public void onEvent(AppLogEvent event) {
        events.add(event);
    }

    public List<AppLogEvent> getEvents() {
        return events;
    }

    public AppLogEvent last() {
        return events.isEmpty() ? null : events.get(events.size() - 1);
    }

    public void clear() {
        events.clear();
    }
}
