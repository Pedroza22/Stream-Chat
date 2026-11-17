package com.streamchat.messaging.rabbitmq.consumer;

import com.streamchat.messaging.rabbitmq.consumer.ProcessedEvent.ProcessedEventId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, ProcessedEventId> {
}
