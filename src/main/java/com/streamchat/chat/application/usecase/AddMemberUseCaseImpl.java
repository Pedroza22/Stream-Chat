package com.streamchat.chat.application.usecase;

import com.streamchat.chat.application.dto.AddMemberCommand;
import com.streamchat.chat.application.port.in.AddMemberUseCase;
import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.domain.exception.GroupNotFoundException;
import com.streamchat.chat.domain.exception.NotGroupMemberException;
import com.streamchat.chat.domain.valueobject.GroupId;
import com.streamchat.chat.domain.valueobject.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
public class AddMemberUseCaseImpl implements AddMemberUseCase {

    private final GroupRepository groupRepository;
    private final Clock clock;

    public AddMemberUseCaseImpl(GroupRepository groupRepository, Clock clock) {
        this.groupRepository = groupRepository;
        this.clock = clock;
    }

    @Override
    public void execute(AddMemberCommand cmd) {
        var group = groupRepository.findById(GroupId.of(cmd.groupId()))
                .orElseThrow(() -> new GroupNotFoundException(cmd.groupId()));

        // Auto-unión: el usuario se agrega a sí mismo — permitido sin ser miembro previo
        boolean isSelfJoin = cmd.requesterId().equals(cmd.targetUserId());
        if (!isSelfJoin) {
            boolean requesterIsMember = group.getMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(cmd.requesterId()));
            if (!requesterIsMember) {
                throw new NotGroupMemberException(cmd.requesterId(), cmd.groupId());
            }
        }

        group.addMember(UserId.of(cmd.targetUserId()), clock);
        groupRepository.save(group);
    }
}
