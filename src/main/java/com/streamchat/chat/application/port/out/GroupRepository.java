package com.streamchat.chat.application.port.out;

import com.streamchat.chat.domain.model.ChatGroup;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.chat.domain.valueobject.UserId;

import java.util.List;
import java.util.Optional;

public interface GroupRepository {
    Optional<ChatGroup> findById(GroupId id);
    ChatGroup save(ChatGroup group);
    List<ChatGroup> findByMemberUserId(UserId userId);
}
