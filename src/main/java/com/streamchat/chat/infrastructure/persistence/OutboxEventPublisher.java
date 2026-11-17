package com.streamchat.chat.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamchat.chat.application.port.out.MessageEventPublisher;
import com.streamchat.contracts.MessageCreatedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxEventPublisher implements MessageEventPublisher {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventPublisher(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(MessageCreatedEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = OutboxEvent.create(event.messageId(), event.eventType(), json);
            outboxRepository.save(outboxEvent);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize MessageCreatedEvent", e);
        }
    }
}
