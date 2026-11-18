package com.streamchat.presence.domain.state;

import com.streamchat.presence.domain.valueobject.TypingKey;

import java.time.Instant;

/**
 * Interfaz del patrón State para el ciclo de vida de escritura de un usuario.
 *
 * Cada estado encapsula su propia lógica de transición, eliminando los
 * condicionales if-else / switch en el código cliente.
 *
 * Transiciones:
 *   (cualquier) --onStarted()--> TypingActiveState
 *   TypingActiveState --onStopped()--> TypingStoppedState
 *   TypingStoppedState --onStopped()--> TypingStoppedState (no-op)
 */
public interface TypingState {

    /** Procesa un evento TYPING_STARTED. Devuelve el nuevo estado. */
    TypingState onStarted(TypingKey key, Instant occurredAt);

    /** Procesa un evento TYPING_STOPPED o expiración por TTL. Devuelve el nuevo estado. */
    TypingState onStopped(TypingKey key, Instant occurredAt);

    /** true si el usuario está actualmente escribiendo. */
    boolean isActive();

    /** Marca temporal del último evento recibido. */
    Instant lastActivityAt();
}
