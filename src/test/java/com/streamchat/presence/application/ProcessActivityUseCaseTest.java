package com.streamchat.presence.application;

import com.streamchat.contracts.ServerEvent;
import com.streamchat.contracts.TypingUpdatePayload;
import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.FakeStatusEventPublisher;
import com.streamchat.presence.application.usecase.ProcessActivityUseCaseImpl;
import com.streamchat.presence.domain.service.TypingStateRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessActivityUseCaseTest {

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

    private TypingStateRegistry registry;
    private FakeStatusEventPublisher publisher;
    private ProcessActivityUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        registry  = new TypingStateRegistry();
        publisher = new FakeStatusEventPublisher();
        useCase   = new ProcessActivityUseCaseImpl(registry, publisher, Clock.fixed(T0, ZoneOffset.UTC));
    }

    private static UserActivityEvent event(String type, UUID groupId, UUID userId) {
        return new UserActivityEvent(UUID.randomUUID(), type, groupId, userId, T0, UUID.randomUUID(), 1);
    }

    @Test
    void typingStarted_registersStateAndPublishesToFanout() {
        UUID groupId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();

        useCase.handle(event("TYPING_STARTED", groupId, userId));

        assertThat(registry.activeCount()).isEqualTo(1);
        List<ServerEvent> events = publisher.getPublished();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).type()).isEqualTo("TYPING_UPDATE");
        TypingUpdatePayload payload = (TypingUpdatePayload) events.get(0).payload();
        assertThat(payload.typing()).isTrue();
        assertThat(payload.userId()).isEqualTo(userId);
        assertThat(payload.groupId()).isEqualTo(groupId);
    }

    @Test
    void typingStopped_removesStateAndPublishesToFanout() {
        UUID groupId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();
        useCase.handle(event("TYPING_STARTED", groupId, userId));
        publisher.clear();

        useCase.handle(event("TYPING_STOPPED", groupId, userId));

        assertThat(registry.activeCount()).isZero();
        List<ServerEvent> events = publisher.getPublished();
        assertThat(events).hasSize(1);
        TypingUpdatePayload payload = (TypingUpdatePayload) events.get(0).payload();
        assertThat(payload.typing()).isFalse();
    }

    @Test
    void duplicateTypingStarted_doesNotPublishAgain() {
        // El patrón State evita notificaciones duplicadas para el mismo estado
        UUID groupId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();
        useCase.handle(event("TYPING_STARTED", groupId, userId));
        publisher.clear();

        // Segundo TYPING_STARTED — ya estaba activo, no debe publicar
        useCase.handle(event("TYPING_STARTED", groupId, userId));

        assertThat(publisher.getPublished()).isEmpty();
    }

    @Test
    void unknownEventType_isIgnored() {
        useCase.handle(event("SOMETHING_ELSE", UUID.randomUUID(), UUID.randomUUID()));

        assertThat(registry.activeCount()).isZero();
        assertThat(publisher.getPublished()).isEmpty();
    }
}
