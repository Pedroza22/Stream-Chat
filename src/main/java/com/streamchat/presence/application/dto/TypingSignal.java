package com.streamchat.presence.application.dto;

/**
 * Señal de escritura enviada por el cliente por WebSocket.
 */
public record TypingSignal(boolean typing) {
}
