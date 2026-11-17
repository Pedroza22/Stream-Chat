package com.streamchat.chat.infrastructure.persistence;

import com.streamchat.chat.domain.model.ChatGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JpaGroupRepository extends JpaRepository<ChatGroup, UUID> {

    @Query("SELECT g FROM ChatGroup g JOIN g.members m WHERE m.userId = :userId")
    List<ChatGroup> findByMemberUserId(@Param("userId") UUID userId);
}
