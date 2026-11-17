package com.streamchat.messaging.rabbitmq.consumer;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @EmbeddedId
    private ProcessedEventId id;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedEvent() {}

    public static ProcessedEvent of(UUID eventId, String consumer) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.id = new ProcessedEventId(eventId, consumer);
        pe.processedAt = Instant.now();
        return pe;
    }

    public ProcessedEventId getId() { return id; }
    public Instant getProcessedAt() { return processedAt; }

    @Embeddable
    public record ProcessedEventId(
            @Column(name = "event_id") UUID eventId,
            @Column(name = "consumer", length = 100) String consumer
    ) implements Serializable {}
}
