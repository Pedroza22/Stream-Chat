package com.streamchat.presence.domain;

import com.streamchat.presence.domain.state.TypingActiveState;
import com.streamchat.presence.domain.state.TypingState;
import com.streamchat.presence.domain.state.TypingStoppedState;
import com.streamchat.presence.domain.valueobject.TypingKey;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests del patrón State para el ciclo de vida de escritura.
 * Sin Spring, sin brokers — dominio puro en memoria.
 */
class TypingStatePatternTest {

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(1);
    private static final Instant T2 = T0.plusSeconds(2);
    private final TypingKey key = new TypingKey(UUID.randomUUID(), UUID.randomUUID());

    // ── TypingStoppedState ──────────────────────────────────────────

    @Test
    void stoppedState_isNotActive() {
        TypingState state = new TypingStoppedState(T0);
        assertThat(state.isActive()).isFalse();
        assertThat(state.lastActivityAt()).isEqualTo(T0);
    }

    @Test
    void stoppedState_onStarted_transitionsToActive() {
        TypingState stopped = new TypingStoppedState(T0);
        TypingState next = stopped.onStarted(key, T1);
        assertThat(next).isInstanceOf(TypingActiveState.class);
        assertThat(next.isActive()).isTrue();
        assertThat(next.lastActivityAt()).isEqualTo(T1);
    }

    @Test
    void stoppedState_onStopped_remainsStopped() {
        TypingState stopped = new TypingStoppedState(T0);
        TypingState next = stopped.onStopped(key, T1);
        assertThat(next).isInstanceOf(TypingStoppedState.class);
        assertThat(next.isActive()).isFalse();
    }

    // ── TypingActiveState ───────────────────────────────────────────

    @Test
    void activeState_isActive() {
        TypingState state = new TypingActiveState(T0);
        assertThat(state.isActive()).isTrue();
        assertThat(state.lastActivityAt()).isEqualTo(T0);
    }

    @Test
    void activeState_onStarted_renewsTimestamp_ifNewer() {
        TypingState active = new TypingActiveState(T0);
        TypingState next = active.onStarted(key, T1);
        assertThat(next).isInstanceOf(TypingActiveState.class);
        assertThat(next.lastActivityAt()).isEqualTo(T1);
    }

    @Test
    void activeState_onStarted_doesNotRollBack_ifOlder() {
        TypingState active = new TypingActiveState(T1);
        TypingState next = active.onStarted(key, T0); // evento más viejo
        assertThat(next.lastActivityAt()).isEqualTo(T1); // no retrocede
    }

    @Test
    void activeState_onStopped_transitionsToStopped() {
        TypingState active = new TypingActiveState(T0);
        TypingState next = active.onStopped(key, T1);
        assertThat(next).isInstanceOf(TypingStoppedState.class);
        assertThat(next.isActive()).isFalse();
        assertThat(next.lastActivityAt()).isEqualTo(T1);
    }

    // ── Cadena de transiciones ──────────────────────────────────────

    @Test
    void fullCycle_stopped_active_stopped() {
        TypingState s = new TypingStoppedState(T0);
        s = s.onStarted(key, T0);   // → Active
        assertThat(s.isActive()).isTrue();
        s = s.onStarted(key, T1);   // → Active (renovado)
        assertThat(s.lastActivityAt()).isEqualTo(T1);
        s = s.onStopped(key, T2);   // → Stopped
        assertThat(s.isActive()).isFalse();
        s = s.onStopped(key, T2);   // → Stopped (no-op)
        assertThat(s.isActive()).isFalse();
    }
}
