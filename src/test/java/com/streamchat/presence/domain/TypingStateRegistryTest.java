package com.streamchat.presence.domain;

import com.streamchat.presence.domain.model.UserTypingState;
import com.streamchat.presence.domain.policy.TtlExpirationPolicy;
import com.streamchat.presence.domain.service.TypingStateRegistry;
import com.streamchat.presence.domain.valueobject.TypingKey;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class TypingStateRegistryTest {

    private static final Instant T0 = Instant.parse("2024-01-01T00:00:00Z");

    private final TypingStateRegistry registry = new TypingStateRegistry();

    private static TypingKey key() {
        return new TypingKey(UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void typingStarted_storesState() {
        TypingKey key = key();

        registry.typingStarted(key, T0);

        assertThat(registry.activeCount()).isEqualTo(1);
        assertThat(registry.snapshot()).extracting(UserTypingState::key).containsExactly(key);
    }

    @Test
    void typingStopped_removesState() {
        TypingKey key = key();
        registry.typingStarted(key, T0);

        registry.typingStopped(key);

        assertThat(registry.activeCount()).isZero();
    }

    @Test
    void outOfOrderEvent_doesNotMoveTimestampBackwards() {
        TypingKey key = key();
        registry.typingStarted(key, T0.plusSeconds(10));

        registry.typingStarted(key, T0.plusSeconds(3));

        assertThat(registry.snapshot()).singleElement()
                .extracting(UserTypingState::lastActivityAt)
                .isEqualTo(T0.plusSeconds(10));
    }

    @Test
    void expire_removesOnlyExpiredStates() {
        TypingKey oldKey = key();
        TypingKey freshKey = key();
        registry.typingStarted(oldKey, T0);
        registry.typingStarted(freshKey, T0.plusSeconds(10));

        var expired = registry.expire(T0.plusSeconds(10), new TtlExpirationPolicy(Duration.ofSeconds(5)));

        assertThat(expired).containsExactly(oldKey);
        assertThat(registry.snapshot()).extracting(UserTypingState::key).containsExactly(freshKey);
    }

    @Test
    void twoUsersWritingSimultaneously_keepSeparateStates() throws InterruptedException {
        TypingKey first = key();
        TypingKey second = key();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        pool.submit(() -> awaitAndStart(start, () -> registry.typingStarted(first, T0)));
        pool.submit(() -> awaitAndStart(start, () -> registry.typingStarted(second, T0)));
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(registry.activeCount()).isEqualTo(2);
        assertThat(registry.snapshot()).extracting(UserTypingState::key).contains(first, second);
    }

    @Test
    void burstOfEvents_thenExpire_returnsActiveCountToZero() throws InterruptedException {
        int threads = 64;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> awaitAndStart(start, () -> registry.typingStarted(key(), T0)));
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(registry.activeCount()).isEqualTo(threads);

        var expired = registry.expire(T0.plusSeconds(5), new TtlExpirationPolicy(Duration.ofSeconds(5)));

        assertThat(expired).hasSize(threads);
        assertThat(registry.activeCount()).isZero();
    }

    private static void awaitAndStart(CountDownLatch start, Runnable action) {
        try {
            start.await();
            action.run();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
