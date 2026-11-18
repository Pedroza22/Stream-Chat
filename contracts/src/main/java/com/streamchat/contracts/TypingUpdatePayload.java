package com.streamchat.contracts;

import java.time.Instant;
import java.util.UUID;

/**
 * Mensaje WebSocket servidor-cliente con el estado de escritura de un usuario.
 * Tipo de evento: {@code TYPING_UPDATE}.
 */
public record TypingUpdatePayload(
        UUID groupId,
        UUID userId,
        boolean typing,
        Instant occurredAt) {
}
