package com.streamchat.messaging.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.chat.infrastructure.persistence.OutboxEvent;
import com.streamchat.chat.infrastructure.persistence.OutboxRepository;
import com.streamchat.contracts.MessageCreatedEvent;
import com.streamchat.messaging.rabbitmq.publisher.PublishException;
import com.streamchat.messaging.rabbitmq.publisher.RabbitMqMessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Relay del patrón Outbox.
 *
 * Problema que resuelve: guardar el mensaje en PostgreSQL y publicarlo en RabbitMQ
 * son dos operaciones distintas. Si el proceso cae entre ambas, el mensaje queda
 * guardado pero nunca llega a los consumidores.
 *
 * Solución: el caso de uso (P1) guarda el mensaje y una fila en outbox_events en la
 * misma transacción. Este relay lee periódicamente las filas PENDING y las publica
 * con publisher confirm. Solo las marca SENT si el broker confirma; de lo contrario
 * las marca FAILED para reintentar en el siguiente ciclo.
 *
 * Garantía: entrega at-least-once. El consumidor es idempotente (ver MessageCreatedConsumer).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 50;

    private final OutboxRepository outboxRepository;
    private final RabbitMqMessagePublisher publisher;
    private final ObjectMapper objectMapper;

    public OutboxRelay(OutboxRepository outboxRepository,
                       RabbitMqMessagePublisher publisher,
                       ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Se ejecuta cada 5 segundos (configurable en app.messaging.outbox-relay-delay-ms).
     * Lee hasta BATCH_SIZE eventos PENDING y los publica.
     */
    @Scheduled(fixedDelayString = "${app.messaging.outbox-relay-delay-ms:5000}")
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> pending = outboxRepository.findPendingEvents(BATCH_SIZE);

        if (pending.isEmpty()) {
            return;
        }

        log.debug("Relay: procesando {} eventos pendientes", pending.size());

        for (OutboxEvent outboxEvent : pending) {
            publishEvent(outboxEvent);
        }
    }

    private void publishEvent(OutboxEvent outboxEvent) {
        try {
            MessageCreatedEvent event = objectMapper.readValue(
                    outboxEvent.getPayload(),
                    MessageCreatedEvent.class
            );

            publisher.publish(event);
            outboxEvent.markSent();

            log.info("Relay: evento publicado eventId={} messageId={}",
                    event.eventId(), event.messageId());

        } catch (PublishException e) {
            outboxEvent.markFailed();
            log.warn("Relay: fallo al publicar outboxEventId={} — se reintentará. Causa: {}",
                    outboxEvent.getId(), e.getMessage());

        } catch (Exception e) {
            outboxEvent.markFailed();
            log.error("Relay: error inesperado procesando outboxEventId={}",
                    outboxEvent.getId(), e);
        }
    }
}
