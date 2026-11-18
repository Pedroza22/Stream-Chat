package com.streamchat.presence.application.port.in;

/**
 * Barrido de expiración: elimina los estados de escritura que superaron su TTL.
 */
public interface ExpireTypingStatesUseCase {
    int expireNow();
}
