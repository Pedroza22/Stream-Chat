package com.streamchat.presence.domain.policy;

import com.streamchat.presence.domain.model.UserTypingState;

import java.time.Instant;

/**
 * Strategy: decide si un estado de escritura debe expirar.
 */
public interface ExpirationPolicy {
    boolean isExpired(UserTypingState state, Instant now);
}
