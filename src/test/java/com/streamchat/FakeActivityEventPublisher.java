package com.streamchat;

import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.port.out.ActivityEventPublisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeActivityEventPublisher implements ActivityEventPublisher {

    private final List<UserActivityEvent> published = new ArrayList<>();

    @Override
    public void publish(UserActivityEvent event) {
        published.add(event);
    }

    public List<UserActivityEvent> getPublished() {
        return Collections.unmodifiableList(published);
    }

    public void clear() {
        published.clear();
    }
}
