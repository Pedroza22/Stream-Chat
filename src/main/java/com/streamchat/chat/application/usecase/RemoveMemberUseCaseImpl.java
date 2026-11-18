package com.streamchat.chat.application.usecase;

import com.streamchat.chat.application.port.in.RemoveMemberUseCase;
import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.domain.exception.GroupNotFoundException;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.chat.domain.valueobject.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class RemoveMemberUseCaseImpl implements RemoveMemberUseCase {

    private final GroupRepository groupRepository;

    public RemoveMemberUseCaseImpl(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Override
    public void execute(UUID requesterId, UUID groupId, UUID targetUserId) {
        var group = groupRepository.findById(GroupId.of(groupId))
                .orElseThrow(() -> new GroupNotFoundException(groupId));

        group.removeMember(UserId.of(requesterId), UserId.of(targetUserId));
        groupRepository.save(group);
    }
}
