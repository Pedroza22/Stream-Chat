package com.streamchat.presence.application.usecase;

import com.streamchat.contracts.ServerEvent;
import com.streamchat.contracts.TypingUpdatePayload;
import com.streamchat.presence.application.port.in.ExpireTypingStatesUseCase;
import com.streamchat.presence.application.port.out.StatusEventPublisher;
import com.streamchat.presence.domain.policy.ExpirationPolicy;
import com.streamchat.presence.domain.service.TypingStateRegistry;
import com.streamchat.presence.domain.valueobject.TypingKey;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Expira estados de escritura que superaron el TTL sin recibir TYPING_STOPPED,
 * y publica el cambio al Fanout Exchange via StatusEventPublisher.
 */
@Service
public class ExpireTypingStatesUseCaseImpl implements ExpireTypingStatesUseCase {

    private final TypingStateRegistry registry;
    private final ExpirationPolicy expirationPolicy;
    private final StatusEventPublisher statusEventPublisher;
    private final Clock clock;

    public ExpireTypingStatesUseCaseImpl(TypingStateRegistry registry,
                                         ExpirationPolicy expirationPolicy,
                                         StatusEventPublisher statusEventPublisher,
                                         Clock clock) {
        this.registry = registry;
        this.expirationPolicy = expirationPolicy;
        this.statusEventPublisher = statusEventPublisher;
        this.clock = clock;
    }

    @Override
    public int expireNow() {
        Instant now = clock.instant();
        List<TypingKey> expired = registry.expire(now, expirationPolicy);

        for (TypingKey key : expired) {
            TypingUpdatePayload payload = new TypingUpdatePayload(key.groupId(), key.userId(), false, now);
            statusEventPublisher.publish(new ServerEvent("TYPING_UPDATE", payload));
        }

        return expired.size();
    }
}
