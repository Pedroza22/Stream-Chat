package com.streamchat.presence.infrastructure.messaging;

import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.port.out.ActivityEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador temporal del puerto {@link ActivityEventPublisher} mientras P2 entrega el adaptador Kafka.
 * Se desactiva definiendo {@code app.presence.publisher=kafka}.
 */
@Component
@ConditionalOnProperty(name = "app.presence.publisher", havingValue = "local", matchIfMissing = true)
public class LocalActivityEventPublisher implements ActivityEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalActivityEventPublisher.class);

    @Override
    public void publish(UserActivityEvent event) {
        log.debug("Evento de actividad (adaptador local): {}", event);
    }
}
