package com.streamchat.chat.application.dto.response;

import java.time.Instant;
import java.util.UUID;

public record GroupResponse(UUID id, String name, UUID createdBy, Instant createdAt) {
}
