package com.streamchat.presence.application.port.in;

import com.streamchat.contracts.UserActivityEvent;

/**
 * Procesa un evento de actividad recibido y actualiza el estado de escritura.
 */
public interface ProcessActivityUseCase {
    void handle(UserActivityEvent event);
}
