package com.streamchat.chat.infrastructure.persistence;

import com.streamchat.chat.domain.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByGroupId(UUID groupId, Pageable pageable);
    long countByGroupId(UUID groupId);
}
