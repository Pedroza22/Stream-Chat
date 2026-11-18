package com.streamchat.messaging.rabbitmq.publisher;

import com.streamchat.chat.application.port.out.MessageEventPublisher;
import com.streamchat.contracts.MessageCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorador que loggea el intento y resultado de cada publicación.
 */
public class LoggingMessageEventPublisher implements MessageEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingMessageEventPublisher.class);

    private final MessageEventPublisher delegate;

    public LoggingMessageEventPublisher(MessageEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(MessageCreatedEvent event) {
        log.info("Publicando evento eventId={} messageId={}", event.eventId(), event.messageId());
        try {
            delegate.publish(event);
            log.info("Evento publicado exitosamente eventId={} messageId={}", event.eventId(), event.messageId());
        } catch (Exception e) {
            log.error("Fallo al publicar evento eventId={} messageId={}", event.eventId(), event.messageId(), e);
            throw e;
        }
    }
}
