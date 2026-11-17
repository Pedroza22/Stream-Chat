package com.streamchat.chat.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "group_members", uniqueConstraints = @UniqueConstraint(columnNames = {"group_id", "user_id"}))
public class GroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private ChatGroup group;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "joined_at")
    private Instant joinedAt;

    protected GroupMember() {
    }

    GroupMember(ChatGroup group, UUID userId, Clock clock) {
        this.group = group;
        this.userId = userId;
        this.joinedAt = Instant.now(clock);
    }

    public Long getId() {
        return id;
    }

    public ChatGroup getGroup() {
        return group;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GroupMember that)) return false;
        return Objects.equals(userId, that.userId)
                && Objects.equals(group != null ? group.getId() : null,
                                  that.group != null ? that.group.getId() : null);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, group != null ? group.getId() : null);
    }
}
