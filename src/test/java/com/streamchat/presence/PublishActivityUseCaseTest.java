package com.streamchat.presence;

import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.usecase.PublishActivityUseCaseImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PublishActivityUseCaseTest {

    private FakeActivityEventPublisher fakePublisher;
    private PublishActivityUseCaseImpl useCase;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneId.of("UTC"));
    private final UUID groupId = UUID.randomUUID();
    private final UUID userId  = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        fakePublisher = new FakeActivityEventPublisher();
        useCase = new PublishActivityUseCaseImpl(fakePublisher, fixedClock);
    }

    @Test
    void typing_started_publica_evento_con_campos_correctos() {
        useCase.publish(groupId, userId, "TYPING_STARTED");

        assertThat(fakePublisher.getPublishedEvents()).hasSize(1);
        UserActivityEvent event = fakePublisher.getLastEvent();

        assertThat(event.eventType()).isEqualTo("TYPING_STARTED");
        assertThat(event.groupId()).isEqualTo(groupId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-11-15T15:00:00Z"));
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.eventId()).isNotNull();
        assertThat(event.correlationId()).isNotNull();
    }

    @Test
    void typing_stopped_publica_evento_correcto() {
        useCase.publish(groupId, userId, "TYPING_STOPPED");

        assertThat(fakePublisher.getLastEvent().eventType()).isEqualTo("TYPING_STOPPED");
    }

    @Test
    void event_type_invalido_lanza_excepcion_sin_publicar() {
        assertThatThrownBy(() -> useCase.publish(groupId, userId, "MENSAJE_ENVIADO"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MENSAJE_ENVIADO");

        assertThat(fakePublisher.getPublishedEvents()).isEmpty();
    }
}
