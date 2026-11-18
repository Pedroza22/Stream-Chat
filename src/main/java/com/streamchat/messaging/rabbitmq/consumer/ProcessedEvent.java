package com.streamchat.messaging.rabbitmq.consumer;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
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

    public ProcessedEventId getId()       { return id; }
    public Instant getProcessedAt()       { return processedAt; }

    /**
     * Clave compuesta: (event_id, consumer).
     *
     * Usamos clase normal (no record) porque Hibernate necesita reflexión mutable
     * para inyectar valores en campos de un @Embeddable. Los records tienen campos
     * final que no admiten inyección por reflexión.
     */
    @Embeddable
    public static class ProcessedEventId implements Serializable {

        @Column(name = "event_id", nullable = false)
        private UUID eventId;

        @Column(name = "consumer", length = 100, nullable = false)
        private String consumer;

        // Constructor vacío requerido por JPA
        protected ProcessedEventId() {}

        public ProcessedEventId(UUID eventId, String consumer) {
            this.eventId  = eventId;
            this.consumer = consumer;
        }

        public UUID getEventId()   { return eventId; }
        public String getConsumer() { return consumer; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ProcessedEventId that)) return false;
            return Objects.equals(eventId, that.eventId)
                    && Objects.equals(consumer, that.consumer);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventId, consumer);
        }
    }
}
