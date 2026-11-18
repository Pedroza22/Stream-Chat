package com.streamchat.messaging.kafka.consumer;

import com.streamchat.chat.application.port.out.MessageRepository;
import com.streamchat.chat.domain.model.ChatMessage;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.MessageCreatedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * analytics_service — Consumidor Kafka del topic "message_audit_log".
 *
 * Lee el log inmutable de mensajes publicado por audit_bridge y persiste
 * cada mensaje en PostgreSQL de forma transaccional, garantizando el
 * historial auditable del chat.
 *
 * También acumula métricas operativas por grupo (mensajes totales).
 *
 * Flujo:
 *   Kafka [message_audit_log]
 *     → MessageAuditConsumer (analytics_service)
 *       → PostgreSQL (tabla messages — idempotente por messageId)
 *
 * Idempotencia: si el mensaje ya existe (mismo messageId), se ignora sin error.
 */
@Component
public class MessageAuditConsumer {

    private static final Logger log = LoggerFactory.getLogger(MessageAuditConsumer.class);
    private static final String AUDIT_TOPIC = "message_audit_log";

    private final MessageRepository messageRepository;
    private final Clock clock;
    private final Counter persistedCounter;
    private final Counter duplicateCounter;

    public MessageAuditConsumer(MessageRepository messageRepository,
                                Clock clock,
                                MeterRegistry meterRegistry) {
        this.messageRepository = messageRepository;
        this.clock = clock;
        this.persistedCounter = Counter.builder("analytics.messages.persisted")
                .description("Mensajes de chat persistidos desde Kafka")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("analytics.messages.duplicate")
                .description("Mensajes duplicados ignorados en auditoría")
                .register(meterRegistry);
    }

    @KafkaListener(
            topics = AUDIT_TOPIC,
            groupId = "streamchat-analytics",
            containerFactory = "auditKafkaListenerContainerFactory"
    )
    public void onAuditEvent(MessageCreatedEvent event, Acknowledgment ack) {
        try {
            persistMessage(event);
            ack.acknowledge();
            log.info("analytics_service: persisted messageId={} groupId={} — Mensajes totales en Grupo {}: {}",
                    event.messageId(), event.groupId(), event.groupId(),
                    messageRepository.countByGroupId(GroupId.of(event.groupId())));
        } catch (Exception e) {
            log.error("analytics_service: failed to persist messageId={}", event.messageId(), e);
            // No hacer ack → Kafka reintentará
        }
    }

    private void persistMessage(MessageCreatedEvent event) {
        GroupId groupId = GroupId.of(event.groupId());

        // Idempotencia: si ya está persistido (vía outbox o reentrega), ignorar
        boolean alreadyExists = messageRepository
                .findByGroupId(groupId, 0, Integer.MAX_VALUE)
                .stream()
                .anyMatch(m -> m.getId().equals(event.messageId()));

        if (alreadyExists) {
            log.debug("analytics_service: duplicate messageId={} — skipping", event.messageId());
            duplicateCounter.increment();
            return;
        }

        ChatMessage message = ChatMessage.create(
                event.groupId(),
                event.senderId(),
                event.content(),
                clock
        );

        messageRepository.save(message);
        persistedCounter.increment();
        log.debug("analytics_service: saved messageId={}", event.messageId());
    }
}
