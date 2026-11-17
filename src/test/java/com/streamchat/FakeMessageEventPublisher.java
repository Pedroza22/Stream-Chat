package com.streamchat;

import com.streamchat.chat.application.port.out.MessageEventPublisher;
import com.streamchat.contracts.MessageCreatedEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FakeMessageEventPublisher implements MessageEventPublisher {

    private final List<MessageCreatedEvent> published = new ArrayList<>();

    @Override
    public void publish(MessageCreatedEvent event) {
        published.add(event);
    }

    public List<MessageCreatedEvent> getPublished() {
        return Collections.unmodifiableList(published);
    }

    public void clear() {
        published.clear();
    }
}
