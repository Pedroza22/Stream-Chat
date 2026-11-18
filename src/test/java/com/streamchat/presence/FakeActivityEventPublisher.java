package com.streamchat.presence;

import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.port.out.ActivityEventPublisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementación fake de ActivityEventPublisher para pruebas unitarias.
 *
 * Permite verificar qué eventos se publicaron sin levantar Kafka.
 * Verifica LSP: cumple el mismo contrato que KafkaActivityEventPublisher.
 */
public class FakeActivityEventPublisher implements ActivityEventPublisher {

    private final List<UserActivityEvent> publishedEvents = new ArrayList<>();

    @Override
    public void publish(UserActivityEvent event) {
        publishedEvents.add(event);
    }

    public List<UserActivityEvent> getPublishedEvents() {
        return Collections.unmodifiableList(publishedEvents);
    }

    public UserActivityEvent getLastEvent() {
        if (publishedEvents.isEmpty()) {
            throw new IllegalStateException("No se publicó ningún evento");
        }
        return publishedEvents.get(publishedEvents.size() - 1);
    }

    public void clear() {
        publishedEvents.clear();
    }
}
