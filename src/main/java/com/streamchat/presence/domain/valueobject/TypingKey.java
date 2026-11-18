package com.streamchat.presence.domain.valueobject;

import java.util.UUID;

/**
 * Clave de un estado de escritura: combinación grupo + usuario.
 */
public record TypingKey(UUID groupId, UUID userId) {

    public TypingKey {
        if (groupId == null || userId == null) {
            throw new IllegalArgumentException("groupId and userId are required");
        }
    }
}
