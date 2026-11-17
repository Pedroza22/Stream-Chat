package com.streamchat.chat.application.usecase;

import com.streamchat.chat.application.dto.CreateGroupCommand;
import com.streamchat.chat.application.port.in.CreateGroupUseCase;
import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.domain.model.ChatGroup;
import com.streamchat.chat.domain.valueobject.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@Transactional
public class CreateGroupUseCaseImpl implements CreateGroupUseCase {

    private final GroupRepository groupRepository;
    private final Clock clock;

    public CreateGroupUseCaseImpl(GroupRepository groupRepository, Clock clock) {
        this.groupRepository = groupRepository;
        this.clock = clock;
    }

    @Override
    public ChatGroup execute(CreateGroupCommand cmd) {
        ChatGroup group = ChatGroup.create(cmd.creatorId(), cmd.name(), clock);
        group.addMember(UserId.of(cmd.creatorId()), clock);
        return groupRepository.save(group);
    }
}
