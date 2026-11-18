package com.streamchat.presence.application;

import com.streamchat.FakeClientNotifier;
import com.streamchat.contracts.TypingUpdatePayload;
import com.streamchat.presence.application.usecase.ExpireTypingStatesUseCaseImpl;
import com.streamchat.presence.domain.policy.TtlExpirationPolicy;
import com.streamchat.presence.domain.service.TypingStateRegistry;
import com.streamchat.presence.domain.valueobject.TypingKey;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExpireTypingStatesUseCaseTest {

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

    private final TypingStateRegistry registry = new TypingStateRegistry();
    private final FakeClientNotifier notifier = new FakeClientNotifier();
    private final UUID groupId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final TypingKey key = new TypingKey(groupId, userId);

    private ExpireTypingStatesUseCaseImpl useCaseAt(Instant now) {
        return new ExpireTypingStatesUseCaseImpl(
                registry,
                new TtlExpirationPolicy(Duration.ofSeconds(5)),
                notifier,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void expiresStaleStateAndNotifiesGroup() {
        registry.typingStarted(key, T0);

        int expired = useCaseAt(T0.plusSeconds(10)).expireNow();

        assertThat(expired).isEqualTo(1);
        assertThat(registry.activeCount()).isZero();
        assertThat(notifier.getNotifications()).hasSize(1);
        TypingUpdatePayload payload = (TypingUpdatePayload) notifier.getNotifications().get(0).getValue().payload();
        assertThat(payload.typing()).isFalse();
        assertThat(payload.userId()).isEqualTo(userId);
    }

    @Test
    void keepsFreshStateWithoutNotifying() {
        registry.typingStarted(key, T0.plusSeconds(9));

        int expired = useCaseAt(T0.plusSeconds(10)).expireNow();

        assertThat(expired).isZero();
        assertThat(registry.activeCount()).isEqualTo(1);
        assertThat(notifier.getNotifications()).isEmpty();
    }
}
