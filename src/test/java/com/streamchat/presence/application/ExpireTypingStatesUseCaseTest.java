package com.streamchat.presence.application;

import com.streamchat.contracts.TypingUpdatePayload;
import com.streamchat.presence.FakeStatusEventPublisher;
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
    private final FakeStatusEventPublisher publisher = new FakeStatusEventPublisher();
    private final UUID groupId = UUID.randomUUID();
    private final UUID userId  = UUID.randomUUID();
    private final TypingKey key = new TypingKey(groupId, userId);

    private ExpireTypingStatesUseCaseImpl useCaseAt(Instant now) {
        return new ExpireTypingStatesUseCaseImpl(
                registry,
                new TtlExpirationPolicy(Duration.ofSeconds(5)),
                publisher,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void expiresStaleStateAndPublishesToFanout() {
        registry.typingStarted(key, T0);

        int expired = useCaseAt(T0.plusSeconds(10)).expireNow();

        assertThat(expired).isEqualTo(1);
        assertThat(registry.activeCount()).isZero();
        assertThat(publisher.getPublished()).hasSize(1);
        TypingUpdatePayload payload = (TypingUpdatePayload) publisher.getPublished().get(0).payload();
        assertThat(payload.typing()).isFalse();
        assertThat(payload.userId()).isEqualTo(userId);
    }

    @Test
    void keepsFreshStateWithoutPublishing() {
        registry.typingStarted(key, T0.plusSeconds(9));

        int expired = useCaseAt(T0.plusSeconds(10)).expireNow();

        assertThat(expired).isZero();
        assertThat(registry.activeCount()).isEqualTo(1);
        assertThat(publisher.getPublished()).isEmpty();
    }
}
