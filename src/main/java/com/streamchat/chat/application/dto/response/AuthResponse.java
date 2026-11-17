package com.streamchat.chat.application.dto.response;

import java.util.UUID;

public record AuthResponse(String token, String username, UUID userId) {
}
