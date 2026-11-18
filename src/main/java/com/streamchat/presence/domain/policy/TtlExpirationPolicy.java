package com.streamchat.presence.domain.policy;

import com.streamchat.presence.domain.model.UserTypingState;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Expira el estado cuando no se renueva dentro del TTL configurado.
 */
public final class TtlExpirationPolicy implements ExpirationPolicy {

    private final Duration ttl;

    public TtlExpirationPolicy(Duration ttl) {
        Objects.requireNonNull(ttl, "ttl is required");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be positive");
        }
        this.ttl = ttl;
    }

    @Override
    public boolean isExpired(UserTypingState state, Instant now) {
        return !state.lastActivityAt().plus(ttl).isAfter(now);
    }
}
