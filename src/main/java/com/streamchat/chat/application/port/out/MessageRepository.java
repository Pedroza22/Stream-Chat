package com.streamchat.chat.application.port.out;

import com.streamchat.chat.domain.model.ChatMessage;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.chat.domain.valueobject.MessageId;

import java.util.List;
import java.util.Optional;

public interface MessageRepository {
    ChatMessage save(ChatMessage message);
    List<ChatMessage> findByGroupId(GroupId id, int page, int size);
    Optional<ChatMessage> findById(MessageId id);
    long countByGroupId(GroupId id);
}
