package com.streamchat.presence.domain.service;

import com.streamchat.presence.domain.model.UserTypingState;
import com.streamchat.presence.domain.policy.ExpirationPolicy;
import com.streamchat.presence.domain.valueobject.TypingKey;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de estados de escritura activos.
 * Usa {@link ConcurrentHashMap} con operaciones atómicas ({@code merge}, {@code remove(key, value)});
 * no hay {@code Map} estático ni bloqueos globales.
 */
public class TypingStateRegistry {

    private final ConcurrentHashMap<TypingKey, UserTypingState> states = new ConcurrentHashMap<>();

    public UserTypingState typingStarted(TypingKey key, Instant occurredAt) {
        return states.merge(
                key,
                UserTypingState.started(key, occurredAt),
                (existing, incoming) -> existing.renewedAt(incoming.lastActivityAt()));
    }

    public Optional<UserTypingState> typingStopped(TypingKey key) {
        return Optional.ofNullable(states.remove(key));
    }

    /**
     * Elimina los estados que cumplen la política de expiración y devuelve sus claves.
     */
    public List<TypingKey> expire(Instant now, ExpirationPolicy policy) {
        List<TypingKey> expired = new ArrayList<>();
        states.forEach((key, state) -> {
            if (policy.isExpired(state, now) && states.remove(key, state)) {
                expired.add(key);
            }
        });
        return expired;
    }

    public Collection<UserTypingState> snapshot() {
        return List.copyOf(states.values());
    }

    public int activeCount() {
        return states.size();
    }
}
