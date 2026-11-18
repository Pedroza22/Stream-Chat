package com.streamchat.messaging.rabbitmq.publisher;

import com.streamchat.chat.application.port.out.MessageEventPublisher;
import com.streamchat.contracts.MessageCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorador (patrón Decorator) que añade logging alrededor de cualquier MessageEventPublisher.
 *
 * Registra el intento de publicación, el resultado exitoso y los fallos,
 * sin mezclar esa responsabilidad en el publicador real (SRP).
 */
public class LoggingPublisher implements MessageEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingPublisher.class);

    private final MessageEventPublisher delegate;

    public LoggingPublisher(MessageEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(MessageCreatedEvent event) {
        log.debug("Publishing event eventId={} eventType={} messageId={}",
                event.eventId(), event.eventType(), event.messageId());
        try {
            delegate.publish(event);
            log.info("Event published successfully eventId={} messageId={}",
                    event.eventId(), event.messageId());
        } catch (Exception e) {
            log.error("Failed to publish event eventId={} messageId={} — {}",
                    event.eventId(), event.messageId(), e.getMessage());
            throw e;
        }
    }
}
