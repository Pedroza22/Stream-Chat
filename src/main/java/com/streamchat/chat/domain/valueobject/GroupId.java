package com.streamchat.chat.domain.valueobject;

import java.util.UUID;

public record GroupId(UUID value) {
    public static GroupId of(UUID id) {
        return new GroupId(id);
    }

    public static GroupId of(String id) {
        return new GroupId(UUID.fromString(id));
    }
}
