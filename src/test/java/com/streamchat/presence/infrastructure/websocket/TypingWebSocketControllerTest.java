package com.streamchat.presence.infrastructure.websocket;

import com.streamchat.chat.infrastructure.security.AuthenticatedUser;
import com.streamchat.presence.application.dto.TypingSignal;
import com.streamchat.presence.application.port.in.ReportTypingUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TypingWebSocketControllerTest {

    private final ReportTypingUseCase reportTypingUseCase = mock(ReportTypingUseCase.class);
    private final TypingWebSocketController controller = new TypingWebSocketController(reportTypingUseCase);

    @Test
    void resolvesUserIdFromSessionAndReportsTyping() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        var user = new AuthenticatedUser(userId, "esteban");
        Principal principal = new UsernamePasswordAuthenticationToken(user, null, List.of());

        controller.typing(groupId, new TypingSignal(true), principal);

        verify(reportTypingUseCase).report(groupId, userId, true);
    }

    @Test
    void supportsAuthenticatedUserAsDirectPrincipal() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        controller.typing(groupId, new TypingSignal(false), new AuthenticatedUser(userId, "esteban"));

        verify(reportTypingUseCase).report(groupId, userId, false);
    }

    @Test
    void rejectsUnauthenticatedSession() {
        Principal anonymous = () -> "anonymous";

        assertThatThrownBy(() -> controller.typing(UUID.randomUUID(), new TypingSignal(true), anonymous))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(reportTypingUseCase);
    }
}
