package com.streamchat.chat.infrastructure.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        log.info("WebSocket session connected: {}", event.getMessage().getHeaders());
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        log.info("WebSocket session disconnected: sessionId={}", event.getSessionId());
    }
}
