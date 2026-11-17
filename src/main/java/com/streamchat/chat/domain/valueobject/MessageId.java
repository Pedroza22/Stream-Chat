package com.streamchat.chat.domain.valueobject;

import java.util.UUID;

public record MessageId(UUID value) {
    public static MessageId of(UUID id) {
        return new MessageId(id);
    }

    public static MessageId of(String id) {
        return new MessageId(UUID.fromString(id));
    }
}
