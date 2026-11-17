package com.streamchat.chat.application.usecase;

import com.streamchat.chat.application.dto.response.MessageResponse;
import com.streamchat.chat.application.dto.response.PageResponse;
import com.streamchat.chat.application.port.in.GetMessageHistoryUseCase;
import com.streamchat.chat.application.port.out.GroupRepository;
import com.streamchat.chat.application.port.out.MessageRepository;
import com.streamchat.chat.domain.exception.GroupNotFoundException;
import com.streamchat.chat.domain.exception.NotGroupMemberException;
import com.streamchat.chat.domain.valueobject.GroupId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class GetMessageHistoryUseCaseImpl implements GetMessageHistoryUseCase {

    private final GroupRepository groupRepository;
    private final MessageRepository messageRepository;

    public GetMessageHistoryUseCaseImpl(GroupRepository groupRepository, MessageRepository messageRepository) {
        this.groupRepository = groupRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public PageResponse<MessageResponse> execute(UUID groupId, UUID requesterId, int page, int size) {
        var group = groupRepository.findById(GroupId.of(groupId))
                .orElseThrow(() -> new GroupNotFoundException(groupId));
        boolean isMember = group.getMembers().stream()
                .anyMatch(m -> m.getUserId().equals(requesterId));
        if (!isMember) {
            throw new NotGroupMemberException(requesterId, groupId);
        }
        List<MessageResponse> list = messageRepository.findByGroupId(GroupId.of(groupId), page, size)
                .stream()
                .map(msg -> new MessageResponse(msg.getId(), msg.getGroupId(), msg.getSenderId(),
                        msg.getContent(), msg.getCreatedAt()))
                .toList();
        long total = messageRepository.countByGroupId(GroupId.of(groupId));
        int totalPages = (int) Math.ceil((double) total / size);
        return new PageResponse<>(list, page, size, total, totalPages);
    }
}
