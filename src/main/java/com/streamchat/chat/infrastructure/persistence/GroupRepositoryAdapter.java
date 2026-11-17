package com.streamchat.chat.infrastructure.persistence;

import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.domain.model.ChatGroup;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.chat.domain.valueobject.UserId;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class GroupRepositoryAdapter implements GroupRepository {

    private final JpaGroupRepository jpaGroupRepository;

    public GroupRepositoryAdapter(JpaGroupRepository jpaGroupRepository) {
        this.jpaGroupRepository = jpaGroupRepository;
    }

    @Override
    public Optional<ChatGroup> findById(GroupId id) {
        return jpaGroupRepository.findById(id.value());
    }

    @Override
    public ChatGroup save(ChatGroup group) {
        return jpaGroupRepository.save(group);
    }

    @Override
    public List<ChatGroup> findByMemberUserId(UserId userId) {
        return jpaGroupRepository.findByMemberUserId(userId.value());
    }
}
