package com.streamchat.presence.infrastructure.websocket;

import com.streamchat.chat.infrastructure.security.AuthenticatedUser;
import com.streamchat.presence.application.port.in.PublishActivityUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Controlador WebSocket para eventos de actividad de typing.
 *
 * El cliente envía:
 *   SEND /app/groups/{groupId}/typing
 *   body: { "eventType": "TYPING_STARTED" }  o  { "eventType": "TYPING_STOPPED" }
 *
 * El userId se extrae de la identidad autenticada (token JWT del handshake).
 * Nunca se acepta el userId del cuerpo del mensaje.
 *
 * Throttling recomendado en el cliente: enviar TYPING_STARTED como máximo
 * cada 3 segundos para actuar como heartbeat.
 */
@Controller
public class ActivityWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ActivityWebSocketController.class);

    private final PublishActivityUseCase publishActivityUseCase;

    public ActivityWebSocketController(PublishActivityUseCase publishActivityUseCase) {
        this.publishActivityUseCase = publishActivityUseCase;
    }

    @MessageMapping("/groups/{groupId}/typing")
    public void handleTyping(@DestinationVariable UUID groupId,
                             TypingRequest request,
                             SimpMessageHeaderAccessor headerAccessor) {
        UUID userId = extractUserId(headerAccessor.getUser());

        log.debug("Typing event groupId={} userId={} eventType={}", groupId, userId, request.eventType());

        publishActivityUseCase.publish(groupId, userId, request.eventType());
    }

    private UUID extractUserId(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser user) {
            return user.userId();
        }
        throw new IllegalStateException("Usuario no autenticado en el canal WebSocket");
    }

    /**
     * Payload del mensaje WebSocket de typing.
     * Solo acepta "TYPING_STARTED" o "TYPING_STOPPED".
     */
    public record TypingRequest(String eventType) {}
}
