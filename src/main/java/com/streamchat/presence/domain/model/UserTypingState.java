package com.streamchat.presence.domain.model;

import com.streamchat.presence.domain.valueobject.TypingKey;

import java.time.Instant;

/**
 * Estado de escritura inmutable de un usuario en un grupo.
 * La marca {@code lastActivityAt} solo avanza: los eventos fuera de orden no la retroceden.
 */
public record UserTypingState(TypingKey key, Instant lastActivityAt) {

    public UserTypingState {
        if (key == null || lastActivityAt == null) {
            throw new IllegalArgumentException("key and lastActivityAt are required");
        }
    }

    public static UserTypingState started(TypingKey key, Instant occurredAt) {
        return new UserTypingState(key, occurredAt);
    }

    public UserTypingState renewedAt(Instant occurredAt) {
        if (occurredAt != null && occurredAt.isAfter(lastActivityAt)) {
            return new UserTypingState(key, occurredAt);
        }
        return this;
    }
}
