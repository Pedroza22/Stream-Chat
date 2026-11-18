package com.streamchat.presence.infrastructure.websocket;

import com.streamchat.chat.infrastructure.security.AuthenticatedUser;
import com.streamchat.presence.application.dto.TypingSignal;
import com.streamchat.presence.application.port.in.ReportTypingUseCase;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Entrada WebSocket de actividad de escritura.
 * Reutiliza el endpoint STOMP {@code /ws} de P1 (prefijo de aplicación {@code /app}):
 * el cliente envía a {@code /app/groups/{groupId}/typing} y no se abre un endpoint nuevo.
 *
 * <p>La identidad del usuario sale de la sesión autenticada en el handshake, nunca del cuerpo.
 * Nota de acoplamiento: {@link AuthenticatedUser} vive en la infraestructura de {@code chat};
 * se reemplazará por un contrato/puerto compartido cuando se revise la sección de contratos.</p>
 */
@Controller
public class TypingWebSocketController {

    private final ReportTypingUseCase reportTypingUseCase;

    public TypingWebSocketController(ReportTypingUseCase reportTypingUseCase) {
        this.reportTypingUseCase = reportTypingUseCase;
    }

    @MessageMapping("/groups/{groupId}/typing")
    public void typing(@DestinationVariable UUID groupId, TypingSignal signal, Principal principal) {
        AuthenticatedUser user = extractUser(principal);
        reportTypingUseCase.report(groupId, user.userId(), signal.typing());
    }

    private static AuthenticatedUser extractUser(Principal principal) {
        if (principal instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser authenticatedUser) {
            return authenticatedUser;
        }
        throw new IllegalStateException("WebSocket session is not authenticated");
    }
}
