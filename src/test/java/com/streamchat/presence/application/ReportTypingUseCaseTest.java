package com.streamchat.presence.application;

import com.streamchat.FakeActivityEventPublisher;
import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.usecase.ReportTypingUseCaseImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportTypingUseCaseTest {

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

    private FakeActivityEventPublisher publisher;
    private ReportTypingUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        publisher = new FakeActivityEventPublisher();
        useCase = new ReportTypingUseCaseImpl(publisher, Clock.fixed(T0, ZoneOffset.UTC));
    }

    @Test
    void reportTypingStarted_publishesVersionedEvent() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        UserActivityEvent event = useCase.report(groupId, userId, true);

        assertThat(event.eventType()).isEqualTo("TYPING_STARTED");
        assertThat(event.groupId()).isEqualTo(groupId);
        assertThat(event.userId()).isEqualTo(userId);
        assertThat(event.occurredAt()).isEqualTo(T0);
        assertThat(event.schemaVersion()).isEqualTo(1);
        assertThat(event.eventId()).isNotNull();
        assertThat(event.correlationId()).isNotNull();
        assertThat(publisher.getPublished()).containsExactly(event);
    }

    @Test
    void reportTypingStopped_publishesStoppedEvent() {
        UserActivityEvent event = useCase.report(UUID.randomUUID(), UUID.randomUUID(), false);

        assertThat(event.eventType()).isEqualTo("TYPING_STOPPED");
        assertThat(publisher.getPublished()).containsExactly(event);
    }
}
