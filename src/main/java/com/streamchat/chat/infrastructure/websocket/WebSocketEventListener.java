package com.streamchat.chat.infrastructure.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.streamchat.chat.infrastructure.security.AuthenticatedUser;

/**
 * Escucha eventos del ciclo de vida de sesiones WebSocket/STOMP.
 * Loguea el usuario autenticado en cada conexión y desconexión.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = extractUsername(accessor);
        log.info("WebSocket connected sessionId={} user={}", accessor.getSessionId(), username);
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String username = extractUsername(accessor);
        log.info("WebSocket disconnected sessionId={} user={}", event.getSessionId(), username);
    }

    private String extractUsername(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof AuthenticatedUser user) {
            return user.username();
        }
        return "anonymous";
    }
}
