package com.streamchat.presence.application.port.in;

import com.streamchat.contracts.UserActivityEvent;

import java.util.UUID;

/**
 * Registra la intención de escritura de un usuario y genera el evento de actividad.
 */
public interface ReportTypingUseCase {
    UserActivityEvent report(UUID groupId, UUID userId, boolean typing);
}
