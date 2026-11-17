package com.streamchat.chat.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    public enum OutboxStatus {
        PENDING, SENT, FAILED
    }

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxStatus status;

    protected OutboxEvent() {
    }

    public static OutboxEvent create(UUID aggregateId, String eventType, String payload) {
        OutboxEvent e = new OutboxEvent();
        e.id = UUID.randomUUID();
        e.aggregateId = aggregateId;
        e.eventType = eventType;
        e.payload = payload;
        e.createdAt = Instant.now();
        e.status = OutboxStatus.PENDING;
        return e;
    }

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getProcessedAt() { return processedAt; }
    public OutboxStatus getStatus() { return status; }

    public void markSent() {
        this.status = OutboxStatus.SENT;
        this.processedAt = Instant.now();
    }

    public void markFailed() {
        this.status = OutboxStatus.FAILED;
        this.processedAt = Instant.now();
    }
}
