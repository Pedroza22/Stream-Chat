package com.streamchat.contracts;

import java.time.Instant;
import java.util.UUID;

public record MessageCreatedEvent(
        UUID eventId,
        String eventType,
        UUID messageId,
        UUID groupId,
        UUID senderId,
        String content,
        Instant createdAt,
        UUID correlationId,
        int schemaVersion) {
}
