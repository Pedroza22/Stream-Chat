package com.streamchat.chat.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    @Query("SELECT o FROM OutboxEvent o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC LIMIT :limit")
    List<OutboxEvent> findPendingEvents(@Param("limit") int limit);
}
