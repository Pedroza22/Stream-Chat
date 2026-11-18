package com.streamchat.presence.application.usecase;

import com.streamchat.chat.application.port.out.ClientNotifier;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.contracts.ServerEvent;
import com.streamchat.contracts.TypingUpdatePayload;
import com.streamchat.presence.application.port.in.ExpireTypingStatesUseCase;
import com.streamchat.presence.domain.policy.ExpirationPolicy;
import com.streamchat.presence.domain.service.TypingStateRegistry;
import com.streamchat.presence.domain.valueobject.TypingKey;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Expira estados de escritura aunque no llegue un TYPING_STOPPED explícito,
 * y avisa a los grupos afectados.
 */
@Service
public class ExpireTypingStatesUseCaseImpl implements ExpireTypingStatesUseCase {

    private final TypingStateRegistry registry;
    private final ExpirationPolicy expirationPolicy;
    private final ClientNotifier clientNotifier;
    private final Clock clock;

    public ExpireTypingStatesUseCaseImpl(TypingStateRegistry registry,
                                         ExpirationPolicy expirationPolicy,
                                         ClientNotifier clientNotifier,
                                         Clock clock) {
        this.registry = registry;
        this.expirationPolicy = expirationPolicy;
        this.clientNotifier = clientNotifier;
        this.clock = clock;
    }

    @Override
    public int expireNow() {
        Instant now = clock.instant();
        List<TypingKey> expired = registry.expire(now, expirationPolicy);
        for (TypingKey key : expired) {
            TypingUpdatePayload payload = new TypingUpdatePayload(key.groupId(), key.userId(), false, now);
            clientNotifier.notifyGroup(GroupId.of(key.groupId()), new ServerEvent("TYPING_UPDATE", payload));
        }
        return expired.size();
    }
}
