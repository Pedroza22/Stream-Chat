package com.streamchat.presence.application.port.in;

import java.util.UUID;

/**
 * Puerto de entrada: publica un evento de actividad de usuario (typing).
 *
 * El eventType válido es "TYPING_STARTED" o "TYPING_STOPPED".
 * El userId sale siempre de la identidad autenticada, nunca del cuerpo del mensaje.
 */
public interface PublishActivityUseCase {
    void publish(UUID groupId, UUID userId, String eventType);
}
