package com.streamchat.chat.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class ChatMessage {

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "sender_id", nullable = false)
    private UUID senderId;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private int version;

    protected ChatMessage() {
    }

    public static ChatMessage create(UUID groupId, UUID senderId, String content, Clock clock) {
        ChatMessage m = new ChatMessage();
        m.id = UUID.randomUUID();
        m.groupId = groupId;
        m.senderId = senderId;
        m.content = content;
        m.createdAt = Instant.now(clock);
        return m;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getVersion() {
        return version;
    }
}
