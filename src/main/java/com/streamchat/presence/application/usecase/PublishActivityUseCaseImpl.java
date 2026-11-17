package com.streamchat.presence.application.usecase;

import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.port.in.PublishActivityUseCase;
import com.streamchat.presence.application.port.out.ActivityEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Caso de uso: publicar un evento de actividad de usuario.
 *
 * Valida que el eventType sea conocido antes de publicar.
 * El userId viene de la identidad autenticada (nunca del body).
 */
@Service
public class PublishActivityUseCaseImpl implements PublishActivityUseCase {

    private static final Set<String> VALID_EVENT_TYPES = Set.of("TYPING_STARTED", "TYPING_STOPPED");

    private final ActivityEventPublisher activityEventPublisher;
    private final Clock clock;

    public PublishActivityUseCaseImpl(ActivityEventPublisher activityEventPublisher, Clock clock) {
        this.activityEventPublisher = activityEventPublisher;
        this.clock = clock;
    }

    @Override
    public void publish(UUID groupId, UUID userId, String eventType) {
        if (!VALID_EVENT_TYPES.contains(eventType)) {
            throw new IllegalArgumentException("eventType inválido: " + eventType);
        }

        UserActivityEvent event = new UserActivityEvent(
                UUID.randomUUID(),
                eventType,
                groupId,
                userId,
                Instant.now(clock),
                UUID.randomUUID(),   // correlationId
                1                    // schemaVersion
        );

        activityEventPublisher.publish(event);
    }
}
