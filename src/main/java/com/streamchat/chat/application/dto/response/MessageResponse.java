package com.streamchat.chat.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(UUID id, UUID groupId, UUID senderId, String content, Instant createdAt) {
}
