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

    public Set<String> getSessionsForGroup(UUID groupId) {
        return groupSessions.getOrDefault(groupId, new CopyOnWriteArraySet<>());
    }
}
