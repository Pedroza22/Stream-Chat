package com.streamchat.presence.domain.state;

import com.streamchat.presence.domain.valueobject.TypingKey;

import java.time.Instant;
import java.util.Objects;

/**
 * Estado: el usuario dejó de escribir (o expiró por TTL).
 *
 * - onStarted() → transiciona a TypingActiveState.
 * - onStopped() → no-op, permanece en este estado.
 */
public final class TypingStoppedState implements TypingState {

    private final Instant lastActivityAt;

    public TypingStoppedState(Instant lastActivityAt) {
        this.lastActivityAt = Objects.requireNonNull(lastActivityAt, "lastActivityAt required");
    }

    @Override
    public TypingState onStarted(TypingKey key, Instant occurredAt) {
        return new TypingActiveState(occurredAt != null ? occurredAt : lastActivityAt);
    }

    @Override
    public TypingState onStopped(TypingKey key, Instant occurredAt) {
        // Ya estaba detenido — no-op
        return this;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public Instant lastActivityAt() {
        return lastActivityAt;
    }

    @Override
    public String toString() {
        return "TypingStoppedState{lastActivityAt=" + lastActivityAt + "}";
    }
}
