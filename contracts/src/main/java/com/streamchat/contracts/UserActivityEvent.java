package com.streamchat.contracts;

import java.time.Instant;
import java.util.UUID;

public record UserActivityEvent(
        UUID eventId,
        String eventType,
        UUID groupId,
        UUID userId,
        Instant occurredAt,
        UUID correlationId,
        int schemaVersion) {
}
