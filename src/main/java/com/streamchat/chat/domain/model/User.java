package com.streamchat.chat.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
    }

    public static User create(String username, String email, String passwordHash, Clock clock) {
        User u = new User();
        u.id = UUID.randomUUID();
        u.username = username;
        u.email = email;
        u.passwordHash = passwordHash;
        u.createdAt = Instant.now(clock);
        return u;
    }

    /** Convenience overload for contexts where clock is not available (tests). */
    public static User create(String username, String email, String passwordHash) {
        return create(username, email, passwordHash, Clock.systemUTC());
    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
