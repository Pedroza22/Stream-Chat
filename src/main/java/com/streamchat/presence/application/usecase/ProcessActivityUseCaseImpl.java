package com.streamchat.presence.application.usecase;

import com.streamchat.chat.application.port.out.ClientNotifier;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.ServerEvent;
import com.streamchat.contracts.TypingUpdatePayload;
import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.port.in.ProcessActivityUseCase;
import com.streamchat.presence.domain.service.TypingStateRegistry;
import com.streamchat.presence.domain.valueobject.TypingKey;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Consume eventos de actividad y mantiene el estado de escritura por grupo y usuario.
 * Las notificaciones salen por el puerto {@link ClientNotifier} de P1.
 */
@Service
public class ProcessActivityUseCaseImpl implements ProcessActivityUseCase {

    static final String TYPING_STARTED = "TYPING_STARTED";
    static final String TYPING_STOPPED = "TYPING_STOPPED";
    static final String TYPING_UPDATE = "TYPING_UPDATE";

    private final TypingStateRegistry registry;
    private final ClientNotifier clientNotifier;
    private final Clock clock;

    public ProcessActivityUseCaseImpl(TypingStateRegistry registry, ClientNotifier clientNotifier, Clock clock) {
        this.registry = registry;
        this.clientNotifier = clientNotifier;
        this.clock = clock;
    }

    @Override
    public void handle(UserActivityEvent event) {
        if (event == null || event.eventType() == null) {
            return;
        }
        TypingKey key = new TypingKey(event.groupId(), event.userId());
        Instant occurredAt = event.occurredAt() != null ? event.occurredAt() : clock.instant();

        switch (event.eventType()) {
            case TYPING_STARTED -> {
                registry.typingStarted(key, occurredAt);
                notifyTyping(key, true, occurredAt);
            }
            case TYPING_STOPPED -> {
                registry.typingStopped(key);
                notifyTyping(key, false, occurredAt);
            }
            default -> {
                // Tipo de evento desconocido: se ignora sin efectos.
            }
        }
    }

    private void notifyTyping(TypingKey key, boolean typing, Instant occurredAt) {
        TypingUpdatePayload payload = new TypingUpdatePayload(key.groupId(), key.userId(), typing, occurredAt);
        clientNotifier.notifyGroup(GroupId.of(key.groupId()), new ServerEvent(TYPING_UPDATE, payload));
    }
}
