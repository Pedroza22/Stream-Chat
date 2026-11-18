package com.streamchat.messaging.rabbitmq.publisher;

import com.streamchat.chat.application.port.out.MessageEventPublisher;
import com.streamchat.contracts.MessageCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorador que reintenta la publicación hasta 3 veces ante {@link PublishException},
 * con 100 ms de espera entre intentos. Al tercer fallo relanza la excepción.
 */
public class RetryingMessageEventPublisher implements MessageEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RetryingMessageEventPublisher.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 100;

    private final MessageEventPublisher delegate;

    public RetryingMessageEventPublisher(MessageEventPublisher delegate) {
        this.delegate = delegate;
    }

    @Override
    public void publish(MessageCreatedEvent event) {
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                delegate.publish(event);
                return;
            } catch (PublishException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    log.warn("Publicación fallida después de {} intentos para eventId={}", MAX_ATTEMPTS, event.eventId());
                    throw e;
                }
                log.warn("Intento {}/{} fallido para eventId={}, reintentando en {}ms",
                        attempt, MAX_ATTEMPTS, event.eventId(), RETRY_DELAY_MS);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
