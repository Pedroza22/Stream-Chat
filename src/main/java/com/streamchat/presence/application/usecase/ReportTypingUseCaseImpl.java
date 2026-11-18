package com.streamchat.presence.application.usecase;

import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.port.in.ReportTypingUseCase;
import com.streamchat.presence.application.port.out.ActivityEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Genera el evento de actividad a partir de la intención de escritura del cliente
 * y lo entrega al puerto {@link ActivityEventPublisher}.
 */
@Service
public class ReportTypingUseCaseImpl implements ReportTypingUseCase {

    private final ActivityEventPublisher activityEventPublisher;
    private final Clock clock;

    public ReportTypingUseCaseImpl(ActivityEventPublisher activityEventPublisher, Clock clock) {
        this.activityEventPublisher = activityEventPublisher;
        this.clock = clock;
    }

    @Override
    public UserActivityEvent report(UUID groupId, UUID userId, boolean typing) {
        UserActivityEvent event = new UserActivityEvent(
                UUID.randomUUID(),
                typing ? "TYPING_STARTED" : "TYPING_STOPPED",
                groupId,
                userId,
                clock.instant(),
                UUID.randomUUID(),
                1);
        activityEventPublisher.publish(event);
        return event;
    }
}
