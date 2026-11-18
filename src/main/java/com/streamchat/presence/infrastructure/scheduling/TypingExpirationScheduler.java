package com.streamchat.presence.infrastructure.scheduling;

import com.streamchat.presence.application.port.in.ExpireTypingStatesUseCase;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Barrido periódico que expira los estados de escritura vencidos.
 */
@Component
public class TypingExpirationScheduler {

    private final ExpireTypingStatesUseCase expireTypingStatesUseCase;

    public TypingExpirationScheduler(ExpireTypingStatesUseCase expireTypingStatesUseCase) {
        this.expireTypingStatesUseCase = expireTypingStatesUseCase;
    }

    @Scheduled(fixedDelayString = "${app.presence.expiration-scan-ms:2000}")
    public void scanExpiredTypingStates() {
        expireTypingStatesUseCase.expireNow();
    }
}
