package com.streamchat.presence.application.usecase;

import com.streamchat.contracts.ServerEvent;
import com.streamchat.contracts.TypingUpdatePayload;
import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.port.in.ProcessActivityUseCase;
import com.streamchat.presence.application.port.out.StatusEventPublisher;
import com.streamchat.presence.domain.service.TypingStateRegistry;
import com.streamchat.presence.domain.state.TypingState;
import com.streamchat.presence.domain.state.TypingStoppedState;
import com.streamchat.presence.domain.valueobject.TypingKey;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Consume eventos de actividad, aplica el patrón State para gestionar
 * transiciones TYPING_STARTED / TYPING_STOPPED, y publica cambios al
 * Fanout Exchange de RabbitMQ via StatusEventPublisher (DIP).
 *
 * El patrón State (TypingActiveState / TypingStoppedState) encapsula
 * las transiciones sin if-else: cada estado sabe cómo reaccionar a
 * cada evento, cumpliendo OCP y eliminando condicionales anidados.
 *
 * Flujo (enunciado §Flujo1, paso 3-4):
 *   Kafka → ActivityEventConsumer → handle()
 *     → TypingState.onStarted/onStopped()   ← patrón State
 *       → StatusEventPublisher.publish()    ← DIP (puerto de salida)
 *         → RabbitMQ Fanout [status_updates]
 *           → StatusUpdatesConsumer (status_backend)
 *             → WebSocket → cliente
 */
@Service
public class ProcessActivityUseCaseImpl implements ProcessActivityUseCase {

    static final String TYPING_STARTED = "TYPING_STARTED";
    static final String TYPING_STOPPED = "TYPING_STOPPED";
    static final String TYPING_UPDATE  = "TYPING_UPDATE";

    private final TypingStateRegistry registry;
    private final StatusEventPublisher statusEventPublisher;
    private final Clock clock;

    // Mapa de estados por clave (groupId:userId) — patrón State
    private final ConcurrentHashMap<TypingKey, TypingState> stateMap = new ConcurrentHashMap<>();

    public ProcessActivityUseCaseImpl(TypingStateRegistry registry,
                                      StatusEventPublisher statusEventPublisher,
                                      Clock clock) {
        this.registry = registry;
        this.statusEventPublisher = statusEventPublisher;
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
            case TYPING_STARTED -> handleStarted(key, occurredAt);
            case TYPING_STOPPED -> handleStopped(key, occurredAt);
            default -> { /* tipo desconocido: ignorar */ }
        }
    }

    private void handleStarted(TypingKey key, Instant occurredAt) {
        TypingState current = stateMap.getOrDefault(key, new TypingStoppedState(occurredAt));
        TypingState next = current.onStarted(key, occurredAt);
        stateMap.put(key, next);

        // Actualizar también el registry de TTL
        registry.typingStarted(key, occurredAt);

        // Publicar al Fanout solo si hubo transición real (detenido → activo)
        if (!current.isActive()) {
            publishTyping(key, true, occurredAt);
        }
    }

    private void handleStopped(TypingKey key, Instant occurredAt) {
        TypingState current = stateMap.getOrDefault(key, new TypingStoppedState(occurredAt));
        TypingState next = current.onStopped(key, occurredAt);
        stateMap.put(key, next);

        registry.typingStopped(key);

        // Publicar al Fanout solo si hubo transición real (activo → detenido)
        if (current.isActive()) {
            publishTyping(key, false, occurredAt);
        }
    }

    private void publishTyping(TypingKey key, boolean typing, Instant occurredAt) {
        TypingUpdatePayload payload = new TypingUpdatePayload(key.groupId(), key.userId(), typing, occurredAt);
        statusEventPublisher.publish(new ServerEvent(TYPING_UPDATE, payload));
    }
}
