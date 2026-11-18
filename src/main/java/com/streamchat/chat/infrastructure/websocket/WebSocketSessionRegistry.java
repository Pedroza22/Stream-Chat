package com.streamchat.chat.infrastructure.websocket;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class WebSocketSessionRegistry {

    private final ConcurrentHashMap<UUID, CopyOnWriteArraySet<String>> groupSessions = new ConcurrentHashMap<>();

    public void register(UUID groupId, String sessionId) {
        groupSessions.computeIfAbsent(groupId, k -> new CopyOnWriteArraySet<>()).add(sessionId);
    }

    public void unregister(UUID groupId, String sessionId) {
        CopyOnWriteArraySet<String> sessions = groupSessions.get(groupId);
        if (sessions != null) {
            sessions.remove(sessionId);
        }
    }

    /**
     * Elimina una sesión de todos los grupos a los que pertenecía.
     *
     * Se invoca al desconectar el WebSocket, donde el groupId ya no está
     * disponible (la suscripción al tópico del grupo ocurrió antes). Recorre
     * todos los grupos y saca la sesión de cada uno, dejando las entradas
     * vacías para que el GC limpie el conjunto.
     */
    public void unregisterSession(String sessionId) {
        for (CopyOnWriteArraySet<String> sessions : groupSessions.values()) {
            sessions.remove(sessionId);
        }
    }

    public Set<String> getSessionsForGroup(UUID groupId) {
        return groupSessions.getOrDefault(groupId, new CopyOnWriteArraySet<>());
    }
}
