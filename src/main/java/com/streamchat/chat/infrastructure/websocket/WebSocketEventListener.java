package com.streamchat.chat.infrastructure.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.UUID;

import com.streamchat.chat.infrastructure.security.AuthenticatedUser;

/**
 * Escucha eventos del ciclo de vida de sesiones WebSocket/STOMP.
 * Loguea el usuario autenticado en cada conexión y desconexión.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);
    private static final String GROUP_TOPIC_PREFIX = "/topic/groups/";

    private final WebSocketSessionRegistry sessionRegistry;

    public WebSocketEventListener(WebSocketSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = extractUsername(accessor);
        log.info("WebSocket connected sessionId={} user={}", accessor.getSessionId(), username);
    }

    /**
     * Registra la sesión en el grupo al que se suscribió.
     *
     * El groupId solo está disponible en la suscripción (destino
     * {@code /topic/groups/{groupId}}), no en el CONNECT. Por eso el registro
     * se hace aquí y no en {@link #handleSessionConnected(SessionConnectedEvent)}.
     */
    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();
        if (destination == null || !destination.startsWith(GROUP_TOPIC_PREFIX)) {
            return;
        }
        String groupIdRaw = destination.substring(GROUP_TOPIC_PREFIX.length());
        try {
            UUID groupId = UUID.fromString(groupIdRaw);
            sessionRegistry.register(groupId, sessionId);
            log.debug("Sesión {} registrada en el grupo {}", sessionId, groupId);
        } catch (IllegalArgumentException e) {
            log.debug("Suscripción con groupId no válido: {}", destination);
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        log.info("WebSocket session disconnected: sessionId={}", event.getSessionId());
    }
}
