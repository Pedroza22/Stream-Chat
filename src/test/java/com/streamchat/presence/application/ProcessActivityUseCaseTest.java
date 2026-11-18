package com.streamchat.presence.application;

import com.streamchat.FakeClientNotifier;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.TypingUpdatePayload;
import com.streamchat.contracts.UserActivityEvent;
import com.streamchat.presence.application.usecase.ProcessActivityUseCaseImpl;
import com.streamchat.presence.domain.service.TypingStateRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessActivityUseCaseTest {

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

    private TypingStateRegistry registry;
    private FakeClientNotifier notifier;
    private ProcessActivityUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        registry = new TypingStateRegistry();
        notifier = new FakeClientNotifier();
        useCase = new ProcessActivityUseCaseImpl(registry, notifier, Clock.fixed(T0, ZoneOffset.UTC));
    }

    private static UserActivityEvent event(String type, UUID groupId, UUID userId) {
        return new UserActivityEvent(UUID.randomUUID(), type, groupId, userId, T0, UUID.randomUUID(), 1);
    }

    @Test
    void typingStarted_registersStateAndNotifiesGroup() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        useCase.handle(event("TYPING_STARTED", groupId, userId));

        assertThat(registry.activeCount()).isEqualTo(1);
        assertThat(notifier.getNotifications()).hasSize(1);
        var notification = notifier.getNotifications().get(0);
        assertThat(notification.getKey()).isEqualTo(GroupId.of(groupId));
        assertThat(notification.getValue().type()).isEqualTo("TYPING_UPDATE");
        TypingUpdatePayload payload = (TypingUpdatePayload) notification.getValue().payload();
        assertThat(payload.typing()).isTrue();
        assertThat(payload.userId()).isEqualTo(userId);
    }

    @Test
    void typingStopped_removesStateAndNotifiesGroup() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        useCase.handle(event("TYPING_STARTED", groupId, userId));

        useCase.handle(event("TYPING_STOPPED", groupId, userId));

        assertThat(registry.activeCount()).isZero();
        assertThat(notifier.getNotifications()).hasSize(2);
        TypingUpdatePayload payload = (TypingUpdatePayload) notifier.getNotifications().get(1).getValue().payload();
        assertThat(payload.typing()).isFalse();
    }

    @Test
    void unknownEventType_isIgnored() {
        useCase.handle(event("SOMETHING_ELSE", UUID.randomUUID(), UUID.randomUUID()));

        assertThat(registry.activeCount()).isZero();
        assertThat(notifier.getNotifications()).isEmpty();
    }
}
