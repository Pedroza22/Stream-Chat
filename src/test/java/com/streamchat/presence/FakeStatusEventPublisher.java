package com.streamchat.presence;

import com.streamchat.contracts.ServerEvent;
import com.streamchat.presence.application.port.out.StatusEventPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * Doble de prueba para StatusEventPublisher.
 * Captura los eventos publicados para verificarlos en tests unitarios.
 */
public class FakeStatusEventPublisher implements StatusEventPublisher {

    private final List<ServerEvent> published = new ArrayList<>();

    @Override
    public void publish(ServerEvent event) {
        published.add(event);
    }

    public List<ServerEvent> getPublished() {
        return List.copyOf(published);
    }

    public void clear() {
        published.clear();
    }
}
