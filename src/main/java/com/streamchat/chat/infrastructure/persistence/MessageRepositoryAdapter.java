package com.streamchat.chat.infrastructure.persistence;

import com.streamchat.chat.application.port.out.MessageRepository;
import com.streamchat.chat.domain.model.ChatMessage;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.chat.domain.valueobject.MessageId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class MessageRepositoryAdapter implements MessageRepository {

    private final JpaMessageRepository jpaMessageRepository;

    public MessageRepositoryAdapter(JpaMessageRepository jpaMessageRepository) {
        this.jpaMessageRepository = jpaMessageRepository;
    }

    @Override
    public ChatMessage save(ChatMessage message) {
        return jpaMessageRepository.save(message);
    }

    @Override
    public List<ChatMessage> findByGroupId(GroupId id, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        return jpaMessageRepository.findByGroupId(id.value(), pageable);
    }

    @Override
    public Optional<ChatMessage> findById(MessageId id) {
        return jpaMessageRepository.findById(id.value());
    }

    @Override
    public long countByGroupId(GroupId id) {
        return jpaMessageRepository.countByGroupId(id.value());
    }
}
