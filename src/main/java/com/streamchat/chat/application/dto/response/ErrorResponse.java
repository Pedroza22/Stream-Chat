package com.streamchat.chat.application.dto.response;

import java.time.Instant;

public record ErrorResponse(int status, String error, String message, Instant timestamp) {
}
