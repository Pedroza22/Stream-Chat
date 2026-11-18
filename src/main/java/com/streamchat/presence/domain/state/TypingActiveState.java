package com.streamchat.presence.domain.state;

import com.streamchat.presence.domain.valueobject.TypingKey;

import java.time.Instant;
import java.util.Objects;

/**
 * Estado: el usuario está escribiendo activamente.
 *
 * - onStarted() → renueva la marca temporal (debounce: solo avanza).
 * - onStopped() → transiciona a TypingStoppedState.
 */
public final class TypingActiveState implements TypingState {

    private final Instant lastActivityAt;

    public TypingActiveState(Instant lastActivityAt) {
        this.lastActivityAt = Objects.requireNonNull(lastActivityAt, "lastActivityAt required");
    }

    @Override
    public TypingState onStarted(TypingKey key, Instant occurredAt) {
        // Debounce: solo actualiza si el evento es más reciente
        if (occurredAt != null && occurredAt.isAfter(lastActivityAt)) {
            return new TypingActiveState(occurredAt);
        }
        return this;
    }

    @Override
    public TypingState onStopped(TypingKey key, Instant occurredAt) {
        return new TypingStoppedState(occurredAt != null ? occurredAt : lastActivityAt);
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    public Instant lastActivityAt() {
        return lastActivityAt;
    }

    @Override
    public String toString() {
        return "TypingActiveState{lastActivityAt=" + lastActivityAt + "}";
    }
}
