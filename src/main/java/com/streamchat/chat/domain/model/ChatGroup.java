package com.streamchat.chat.domain.model;

import com.streamchat.chat.domain.exception.NotGroupMemberException;
import com.streamchat.chat.domain.valueobject.UserId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "chat_groups")
public class ChatGroup {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, fetch = FetchType.EAGER, orphanRemoval = true)
    private Set<GroupMember> members = new HashSet<>();

    protected ChatGroup() {
    }

    public static ChatGroup create(UUID createdBy, String name, Clock clock) {
        ChatGroup g = new ChatGroup();
        g.id = UUID.randomUUID();
        g.name = name;
        g.createdBy = createdBy;
        g.createdAt = Instant.now(clock);
        return g;
    }

    public void addMember(UserId userId, Clock clock) {
        boolean alreadyMember = members.stream().anyMatch(m -> m.getUserId().equals(userId.value()));
        if (!alreadyMember) {
            members.add(new GroupMember(this, userId.value(), clock));
        }
    }

    public boolean isMember(UserId userId) {
        return members.stream().anyMatch(m -> m.getUserId().equals(userId.value()));
    }

    public ChatMessage sendMessage(UserId senderId, String content, Clock clock) {
        if (!isMember(senderId)) {
            throw new NotGroupMemberException(senderId.value(), id);
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content cannot be blank");
        }
        if (content.length() > 4000) {
            throw new IllegalArgumentException("Content too long (max 4000 characters)");
        }
        return ChatMessage.create(id, senderId.value(), content, clock);
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<GroupMember> getMembers() {
        return members;
    }
}
